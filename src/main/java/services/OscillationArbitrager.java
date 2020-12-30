package services;

import buffer.TradeBuffer;
import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import config.Configuration;
import constants.Exchange;
import org.apache.commons.lang3.time.StopWatch;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class OscillationArbitrager implements EventHandler<OrderBookEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(OscillationArbitrager.class);

    //Sort OrderBooks by the natural order defined for a LimitOrder (ascending)
    private static final Comparator<Map.Entry<Exchange, OrderBook>> comparator = (e1, e2) -> {
        if (((Exchange)e1.getKey()).name().equals(((Exchange)e2.getKey()).name())) return 0;
        return e1.getValue().getAsks().get(0).compareTo(e2.getValue().getAsks().get(0));
    };

    private MetadataAggregator metadataAggregator;
    private TradeBuffer tradeBuffer;
    private Map<CurrencyPair, TreeSet<Entry<Exchange, OrderBook>>> orderBooks = new ConcurrentHashMap<>();

    private BigDecimal minGain;

    private StopWatch stopWatch;

    public OscillationArbitrager(Configuration cfg,
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

    //Make sure you unit test this...basically broke the app
    public void upsertOrderBook(Exchange exchange, CurrencyPair currencyPair, OrderBook orderBook) {
        orderBooks.computeIfAbsent(currencyPair, (k) -> {
           return new TreeSet(comparator);
        });

        Entry<Exchange, OrderBook> entry = new Entry(exchange, orderBook);
        if (orderBooks.get(currencyPair).contains(entry)) {
            orderBooks.get(currencyPair).remove(entry);
        }
        orderBooks.get(currencyPair).add(entry);
        computeTrades(currencyPair);
    }

    public TreeSet<Entry<Exchange, OrderBook>> getOrderBooks(CurrencyPair currencyPair) {
        return orderBooks.get(currencyPair);
    }

    public void computeTrades(CurrencyPair currencyPair) {
//        LOG.info("this is my compute thread");
        if (!stopWatch.isStarted()) {
            stopWatch.start();
        } else {
            stopWatch.resume();
        }

        try {
            //Not enough exchanges to analyze price deviations
            if (orderBooks.get(currencyPair).size() <= 1) {
                LOG.debug("Currency Pair: {} does not possess the minimum number of exchanges to perform oscillation arbitrage analysis", currencyPair);
            } else {
                //Point to the OrderBook with the lowest ask/bid
                Iterator ascendingIterator = orderBooks.get(currencyPair).iterator();
                //Point to the OrderBook with the highest ask/bid
                Iterator descendingIterator = orderBooks.get(currencyPair).descendingIterator();

                //For now, use whichever fee (maker or taker) is greatest as the effective fee
                while (ascendingIterator.hasNext() && descendingIterator.hasNext()) {
                    Entry<Exchange, OrderBook> ex1 = (Entry) ascendingIterator.next();
                    BigDecimal ex1MakerFee = metadataAggregator.getMakerFee(ex1.key, currencyPair);
                    BigDecimal ex1TakerFee = metadataAggregator.getTakerFee(ex1.key, currencyPair);
                    BigDecimal ex1LowestAsk = ex1.value.getAsks().get(0).getLimitPrice();

                    Entry<Exchange, OrderBook> ex2 = (Entry) descendingIterator.next();
                    BigDecimal ex2MakerFee = metadataAggregator.getMakerFee(ex2.key, currencyPair);
                    BigDecimal ex2TakerFee = metadataAggregator.getTakerFee(ex2.key, currencyPair);
                    BigDecimal ex2HighestBid = ex2.value.getBids().get(0).getLimitPrice();

                    BigDecimal netBuyAmount = ex1LowestAsk.multiply(BigDecimal.ONE.subtract(ex1TakerFee));
                    BigDecimal netSellAmount = ex2HighestBid.multiply(BigDecimal.ONE.subtract(ex2TakerFee));

                    if (netSellAmount.compareTo(netBuyAmount) > 0
                            && netSellAmount.subtract(netBuyAmount).divide(netBuyAmount, 5, RoundingMode.HALF_EVEN).compareTo(minGain) >= 0) {
                        LOG.info("Arbitrage Opportunity Detected! Buy on {} at {}, Sell on {} at {}",
                                ex1.key, ex1LowestAsk, ex2.key, ex2HighestBid);
                        LOG.info("With fees calculated, Buy Net Amount: {}, Sell Net Amount: {}",
                                netBuyAmount, netSellAmount);
                    } else {
                        LOG.debug("Nope.");
                        break;
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
