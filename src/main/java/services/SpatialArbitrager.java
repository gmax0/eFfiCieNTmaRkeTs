package services;

import buffer.TradeBuffer;
import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import config.Configuration;
import domain.Trade;
import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import org.apache.commons.lang3.time.StopWatch;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static domain.constants.OrderActionType.ASK;
import static domain.constants.OrderActionType.BID;
import static domain.constants.OrderType.LIMIT;

/**
 *
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

    private BigDecimal minGain;

    private StopWatch stopWatch;

    public SpatialArbitrager(Configuration cfg,
                             MetadataAggregator metadataAggregator,
                             TradeBuffer tradeBuffer) {
        this.minGain = cfg.getOscillationArbitragerConfig().getMinGain();

        this.metadataAggregator = metadataAggregator;
        this.tradeBuffer = tradeBuffer;
        this.stopWatch = new StopWatch();
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

                    //For now, use taker fee as default
                    BigDecimal ex1MakerFee = metadataAggregator.getMakerFee(ex1.key, currencyPair);
                    BigDecimal ex1TakerFee = metadataAggregator.getTakerFee(ex1.key, currencyPair);
                    BigDecimal ex1LowestAsk = ex1.value.getAsks().get(0).getLimitPrice();
                    BigDecimal ex1HighestBid = ex1.value.getBids().get(0).getLimitPrice(); //To determine price level ceiling for a maker order
                    BigDecimal ex1EffectivePrice = ex1LowestAsk;

                    BigDecimal ex2MakerFee = metadataAggregator.getMakerFee(ex2.key, currencyPair);
                    BigDecimal ex2TakerFee = metadataAggregator.getTakerFee(ex2.key, currencyPair);
                    BigDecimal ex2LowestAsk = ex2.value.getAsks().get(0).getLimitPrice(); // To determine price level floor for a taker order
                    BigDecimal ex2HighestBid = ex2.value.getBids().get(0).getLimitPrice();
                    BigDecimal ex2HighestBidVolume = ex2.value.getBids().get(0).getOriginalAmount();
                    BigDecimal ex2EffectivePrice = ex2HighestBid;


                    /*
                    BigDecimal netBuyAmount = ex1EffectivePrice.divide(ex2HighestBidVolume, 5, RoundingMode.HALF_EVEN).multiply(BigDecimal.ONE.subtract(ex1TakerFee));
                    BigDecimal netSellAmount = ex2EffectivePrice.multiply(ex2HighestBidVolume).multiply(BigDecimal.ONE.subtract(ex2TakerFee));

                    if (netSellAmount.compareTo(netBuyAmount) > 0
                            && netSellAmount.subtract(netBuyAmount).divide(netBuyAmount, 5, RoundingMode.HALF_EVEN).compareTo(minGain) >= 0) {
                        LOG.info("Arbitrage Opportunity Detected for {} ! Buy {} units on {} at {}, Sell {} units on {} at {}",
                                currencyPair, ex2HighestBidVolume, ex1.key, ex1EffectivePrice, ex2HighestBidVolume, ex2.key, ex2EffectivePrice);
                        LOG.info("With fees calculated, Buy Net Amount: {}, Sell Net Amount: {}",
                                netBuyAmount, netSellAmount);
                    } else {
                        LOG.debug("Nope.");
                        break;
                    }

                     */
                    BigDecimal costToBuy = ex1EffectivePrice.multiply(ex2HighestBidVolume).divide(BigDecimal.ONE.subtract(ex1TakerFee), 5, RoundingMode.HALF_EVEN);
                    BigDecimal totalSold = ex2EffectivePrice.multiply(ex2HighestBidVolume).multiply(BigDecimal.ONE.subtract(ex2TakerFee));

                    if (totalSold.compareTo(costToBuy) > 0
                            && totalSold.subtract(costToBuy).divide(costToBuy, 5, RoundingMode.HALF_EVEN).compareTo(minGain) >= 0) {
                        LOG.info("Arbitrage Opportunity Detected for {} ! Buy {} units on {} at {}, Sell {} units on {} at {}",
                                currencyPair, ex2HighestBidVolume, ex1.key, ex1EffectivePrice, ex2HighestBidVolume, ex2.key, ex2EffectivePrice);
                        LOG.info("With fees calculated, Cost To Buy: {} , Amount Sold: {}, Profit: {}",
                                costToBuy, totalSold, totalSold.subtract(costToBuy));

                        tradeBuffer.insert(Trade.builder()
                                .exchange(ex1.key)
                                .currencyPair(currencyPair)
                                .orderActionType(BID)
                                .orderType(LIMIT)
                                .price(ex1EffectivePrice)
                                .amount(ex2HighestBidVolume)
                                .build());
                        tradeBuffer.insert(Trade.builder()
                                .exchange(ex2.key)
                                .currencyPair(currencyPair)
                                .orderActionType(ASK)
                                .orderType(LIMIT)
                                .price(ex2EffectivePrice)
                                .amount(ex2HighestBidVolume)
                                .build());
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
