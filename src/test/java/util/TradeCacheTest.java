package util;

import domain.Trade;
import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.junit.Assert.*;

public class TradeCacheTest {

    TradeCache tradeCache;

    @Before
    public void setup() {
        tradeCache = new TradeCache(new BigDecimal(1.0));
    }

    @Test
    public void tradesSortedByAscendingTimeDiscovered() {
        Trade trade1 = Trade.builder()
                .exchange(Exchange.COINBASE_PRO)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .timeDiscovered(Instant.now())
                .build();
        Trade trade2 = Trade.builder()
                .exchange(Exchange.BITFINEX)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.ASK)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(101))
                .amount(new BigDecimal(1.0))
                .timeDiscovered(Instant.now().plusNanos(1))
                .build();

        tradeCache.insertTrade(trade1);
        tradeCache.insertTrade(trade2);
        TreeSet<Trade> trades = tradeCache.getCachedTrades();

        assertEquals(trade1, trades.pollFirst());
        assertEquals(trade2, trades.pollLast());
    }

    @Test
    public void clearExpiredTrades_noTradesExpired() {
        Trade trade1 = Trade.builder()
                .exchange(Exchange.COINBASE_PRO)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .timeDiscovered(Instant.now())
                .build();
        Trade trade2 = Trade.builder()
                .exchange(Exchange.BITFINEX)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.ASK)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(101))
                .amount(new BigDecimal(1.0))
                .timeDiscovered(Instant.now().plusNanos(1))
                .build();

        tradeCache.insertTrade(trade1);
        tradeCache.insertTrade(trade2);
        tradeCache.clearExpiredTrades();

        assertEquals(2, tradeCache.getCachedTrades().size());
        assertTrue(tradeCache.containsTrade(trade1));
        assertTrue(tradeCache.containsTrade(trade2));
    }

    @Test
    public void clearExpiredTrades_tradesExpired() throws Exception {
        Trade trade1 = Trade.builder()
                .exchange(Exchange.COINBASE_PRO)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .timeDiscovered(Instant.now().minusSeconds(2))
                .build();
        Trade trade2 = Trade.builder()
                .exchange(Exchange.BITFINEX)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.ASK)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(101))
                .amount(new BigDecimal(1.0))
                .timeDiscovered(Instant.now().minusSeconds(2))
                .build();

        tradeCache.insertTrade(trade1);
        tradeCache.insertTrade(trade2);
        tradeCache.clearExpiredTrades();

        assertEquals(0, tradeCache.getCachedTrades().size());
    }
}