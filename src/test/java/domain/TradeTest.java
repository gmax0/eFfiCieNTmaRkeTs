package domain;

import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class TradeTest {

    @Test
    public void testHashCode_equivalentHashes() {
        Trade trade1 = Trade.builder()
                .exchange(Exchange.COINBASE_PRO)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .build();
        Trade trade2 = Trade.builder()
                .exchange(Exchange.COINBASE_PRO)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .build();

        assertEquals(trade1.hashCode(), trade2.hashCode());
    }

    @Test
    public void testHashCode_unequalHashes() {
        Trade trade1 = Trade.builder()
                .exchange(Exchange.COINBASE_PRO)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .build();
        Trade trade2 = Trade.builder()
                .exchange(Exchange.BITFINEX)
                .currencyPair(CurrencyPair.BTC_USD)
                .orderActionType(OrderActionType.BID)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(100))
                .amount(new BigDecimal(1.0))
                .build();

        assertNotEquals(trade1.hashCode(), trade2.hashCode());
    }
}