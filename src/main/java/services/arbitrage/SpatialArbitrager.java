package services.arbitrage;

import buffer.TradeBuffer;
import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import config.Configuration;
import domain.Trade;
import domain.constants.Exchange;
import org.apache.commons.lang3.time.StopWatch;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;
import util.TradeCache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static domain.constants.OrderType.LIMIT;
import static org.knowm.xchange.dto.Order.OrderType.ASK;
import static org.knowm.xchange.dto.Order.OrderType.BID;

/**
 * A basic, naive spatial arbitrage algorithm for initial testing purposes.
 */
public class SpatialArbitrager implements EventHandler<OrderBookEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SpatialArbitrager.class);

    //Sort OrderBooks in ascending order according to each book's first ask
    private static final Comparator<Map.Entry<Exchange, OrderBook>> ascendingAskComparator = (e1, e2) -> {
        if (e1.getKey().name().equals(e2.getKey().name())) return 0;
        return e1.getValue().getAsks().get(0).compareTo(e2.getValue().getAsks().get(0));
    };

    //Sort OrderBooks in descending order according to each book's first bid
    private static final Comparator<Map.Entry<Exchange, OrderBook>> descendingBidComparator = (e1, e2) -> {
        if (e1.getKey().name().equals(e2.getKey().name())) return 0;
        return e1.getValue().getBids().get(0).compareTo(e2.getValue().getBids().get(0));
    };

    private MetadataAggregator metadataAggregator;
    private TradeBuffer tradeBuffer;

    final private Map<CurrencyPair, TreeSet<Entry<Exchange, OrderBook>>> orderBooksAscendingAsks = new ConcurrentHashMap<>();
    final private Map<CurrencyPair, TreeSet<Entry<Exchange, OrderBook>>> orderBooksDescendingBids = new ConcurrentHashMap<>();

    //TODO: Come up with something more elegant
    private TradeCache tradeCache;
    private BigDecimal cacheTime;

    private BigDecimal minGain;
    private StopWatch stopWatch = new StopWatch();

    public SpatialArbitrager(Configuration cfg,
                             MetadataAggregator metadataAggregator,
                             TradeBuffer tradeBuffer) {
        this.minGain = cfg.getSpatialArbitragerConfig().getMinGain();
        this.cacheTime = cfg.getSpatialArbitragerConfig().getCacheTime();
        this.tradeCache = new TradeCache(this.cacheTime);

        this.metadataAggregator = metadataAggregator;
        this.tradeBuffer = tradeBuffer;
    }

    public class Entry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private V value;
        public Entry(final K key) {
            this.key = key;
        }
        public Entry(final K key, final V value) {
            this.key = key;
            this.value = value;
        }
        public K getKey() {
            return key;
        }
        public V getValue() {
            return value;
        }
        public V setValue(final V value) {
            final V oldValue = this.value;
            this.value = value;
            return oldValue;
        }
    }

    @Override
    public void onEvent(OrderBookEvent event, long sequence, boolean endOfBatch) {
        this.upsertOrderBook(event.exchange, event.currencyPair, event.orderBook);
    }

    //TODO: Not sure why but long-running executions results in a single exchange with a duplicate entry in the TreeSet...
    public void upsertOrderBook(Exchange exchange, CurrencyPair currencyPair, OrderBook orderBook) {
        orderBooksAscendingAsks.computeIfAbsent(currencyPair, (k) -> {
           return new TreeSet(ascendingAskComparator);
        });
        orderBooksDescendingBids.computeIfAbsent(currencyPair, (k) -> {
            return new TreeSet(descendingBidComparator);
        });

        Entry<Exchange, OrderBook> entry = new Entry(exchange, orderBook);
        if (orderBooksAscendingAsks.get(currencyPair).contains(entry)) {
            orderBooksAscendingAsks.get(currencyPair).remove(entry);
        }
        if (orderBooksDescendingBids.get(currencyPair).contains(entry)) {
            orderBooksDescendingBids.get(currencyPair).remove(entry);
        }

        orderBooksAscendingAsks.get(currencyPair).add(entry);
        orderBooksDescendingBids.get(currencyPair).add(entry);
        computeTrades(currencyPair);
    }

    public TreeSet<Entry<Exchange, OrderBook>> getOrderBooksAscendingAsks(CurrencyPair currencyPair) {
        return orderBooksAscendingAsks.get(currencyPair);
    }

    public TreeSet<Entry<Exchange, OrderBook>> getOrderBooksDescendingBids(CurrencyPair currencyPair) {
        return orderBooksDescendingBids.get(currencyPair);
    }

    //TODO: Improve this algorithm, currently just a barebones proof-of-concept
    public void computeTrades(CurrencyPair currencyPair) {
        if (!stopWatch.isStarted()) {
            stopWatch.start();
        } else {
            stopWatch.resume();
        }

        try {
            if (orderBooksAscendingAsks.size() != orderBooksDescendingBids.size()) {
                LOG.error("Different TreeSet sizes for currency pair {}", currencyPair);
                return;
            }

            //Not enough exchanges to analyze price deviations
            if (orderBooksAscendingAsks.get(currencyPair).size() <= 1) {
                LOG.debug("Currency Pair: {} does not possess the minimum number of exchanges to perform oscillation arbitrage analysis", currencyPair);
            } else {
                //Point to the OrderBook with the lowest ask/bid
                Iterator askIterator = orderBooksAscendingAsks.get(currencyPair).iterator();
                //Point to the OrderBook with the highest ask/bid
                Iterator bidIterator = orderBooksDescendingBids.get(currencyPair).iterator();

                while (askIterator.hasNext() && bidIterator.hasNext()) {
                    Entry<Exchange, OrderBook> ex1 = (Entry) askIterator.next();
                    Entry<Exchange, OrderBook> ex2 = (Entry) bidIterator.next();

                    if (ex1.key.equals(ex2.key)) {
                        if (askIterator.hasNext()) {
                            askIterator.next();
                        }
                        continue;
                    }

                    //TODO: Optimize this section if necessary
                    //For now, use taker fee as default
                    Integer ex1PriceScale = metadataAggregator.getPriceScale(ex1.key, currencyPair);
                    BigDecimal ex1MinOrderAmount = metadataAggregator.getMinimumOrderAmount(ex1.key, currencyPair);
                    BigDecimal ex1MaxOrder;
                    Fee ex1Fees = metadataAggregator.getFees(ex1.key, currencyPair);
                    BigDecimal ex1MakerFee = ex1Fees.getMakerFee();
                    BigDecimal ex1TakerFee = ex1Fees.getTakerFee();
                    BigDecimal ex1LowestAskPrice = ex1.value.getAsks().get(0).getLimitPrice();
                    BigDecimal ex1LowestAskVolume = ex1.value.getAsks().get(0).getOriginalAmount();
                    BigDecimal ex1HighestBid = ex1.value.getBids().get(0).getLimitPrice(); //To determine price level ceiling for a maker order
                    BigDecimal ex1EffectivePrice = ex1LowestAskPrice;

                    Integer ex2PriceScale = metadataAggregator.getPriceScale(ex2.key, currencyPair);
                    BigDecimal ex2MinOrderAmount = metadataAggregator.getMinimumOrderAmount(ex2.key, currencyPair);
                    BigDecimal ex2MaxOrder;
                    Fee ex2Fees = metadataAggregator.getFees(ex2.key, currencyPair);
                    BigDecimal ex2MakerFee = ex2Fees.getMakerFee();
                    BigDecimal ex2TakerFee = ex2Fees.getTakerFee();
                    BigDecimal ex2LowestAsk = ex2.value.getAsks().get(0).getLimitPrice(); // To determine price level floor for a maker order
                    BigDecimal ex2HighestBidPrice = ex2.value.getBids().get(0).getLimitPrice();
                    BigDecimal ex2HighestBidVolume = ex2.value.getBids().get(0).getOriginalAmount();
                    BigDecimal ex2EffectivePrice = ex2HighestBidPrice;

                    BigDecimal effectiveBaseOrderVolume = ex1LowestAskVolume.min(ex2HighestBidVolume);
                    if (effectiveBaseOrderVolume.compareTo(ex1MinOrderAmount) < 0 || effectiveBaseOrderVolume.compareTo(ex2MinOrderAmount) < 0) {
                        continue;
                    }

                    //Check for costToBuy = 0: .02371 * .0000004 / (1 - .0026)
                    //Prices AND Fees are assumed to be in the quote currency
                    BigDecimal costToBuy = ex1EffectivePrice.multiply(effectiveBaseOrderVolume);
                    BigDecimal totalCostToBuy = costToBuy.add(costToBuy.multiply(ex1TakerFee));

                    BigDecimal incomeSold = ex2EffectivePrice.multiply(effectiveBaseOrderVolume);
                    BigDecimal totalIncomeSold = incomeSold.subtract(incomeSold.multiply(ex2TakerFee));

                    Trade buyLow = Trade.builder()
                            .exchange(ex1.key)
                            .currencyPair(currencyPair)
                            .orderActionType(BID)
                            .orderType(LIMIT)
                            .price(ex1EffectivePrice)
                            .amount(effectiveBaseOrderVolume)
                            .timeDiscovered(Instant.now())
                            .fee(ex1TakerFee)
                            .build();
                    Trade sellHigh = Trade.builder()
                            .exchange(ex2.key)
                            .currencyPair(currencyPair)
                            .orderActionType(ASK)
                            .orderType(LIMIT)
                            .price(ex2EffectivePrice)
                            .amount(effectiveBaseOrderVolume)
                            .timeDiscovered(Instant.now())
                            .fee(ex2TakerFee)
                            .build();

                    if (totalIncomeSold.compareTo(totalCostToBuy) > 0
                            && totalIncomeSold.subtract(totalCostToBuy).divide(totalCostToBuy, 5, RoundingMode.HALF_EVEN).compareTo(minGain) >= 0
                            && !tradeCache.containsTrade(buyLow) && !tradeCache.containsTrade(sellHigh)) {
                        tradeCache.insertTrade(buyLow);
                        tradeCache.insertTrade(sellHigh);

                        LOG.info("Arbitrage Opportunity Detected for {} ! Buy {} units on {} at {}, Sell {} units on {} at {}",
                                currencyPair, effectiveBaseOrderVolume, ex1.key, ex1EffectivePrice, effectiveBaseOrderVolume, ex2.key, ex2EffectivePrice);
                        LOG.info("With fees calculated, Cost To Buy: {} , Amount Sold: {}, Profit: {}",
                                totalCostToBuy, totalIncomeSold, totalIncomeSold.subtract(totalCostToBuy));
                        tradeBuffer.insert(buyLow, sellHigh);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Exception caught while performing computation for {}", currencyPair, e);
        }

        stopWatch.suspend();
        LOG.debug("OscillationArbitrager Execution Time (nanoseconds): {}", Long.toString(stopWatch.getNanoTime()));
        stopWatch.reset();
    }


}
