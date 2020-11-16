package services;

import buffer.TradeBuffer;
import config.Configuration;
import constants.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import static constants.Exchange.BITFINEX;
import static org.junit.Assert.*;
import static org.knowm.xchange.currency.CurrencyPair.BTC_USD;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OscillationArbitragerTest {

    @InjectMocks
    OscillationArbitrager oscillationArbitrager;

    @Spy Configuration mockConfig = Configuration.builder()
            .oscillationArbitragerConfig(Configuration.OscillationArbitragerConfig.builder()
                    .minGain(new BigDecimal(.001))
                    .build())
            .build();
    @Mock MetadataAggregator mockMetadataAggregator;
    @Mock TradeBuffer tradeBuffer;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void onEvent() {
    }

    //TODO: Generate orderbooks from .csv files
    @Test
    public void upsertOrderBook_testForDuplicates() {
        LimitOrder bid1 = new LimitOrder(Order.OrderType.BID, BigDecimal.ONE, null, null, null, new BigDecimal(15000));
        LimitOrder ask1 = new LimitOrder(Order.OrderType.BID, BigDecimal.ONE, null, null, null, new BigDecimal(15001));

        LimitOrder bid2 = new LimitOrder(Order.OrderType.BID, BigDecimal.ONE, null, null, null, new BigDecimal(14999));
        LimitOrder ask2 = new LimitOrder(Order.OrderType.BID, BigDecimal.ONE, null, null, null, new BigDecimal(15000));

        oscillationArbitrager.upsertOrderBook(BITFINEX, BTC_USD, new OrderBook(new Date(), Arrays.asList(ask1), Arrays.asList(bid1)));
        oscillationArbitrager.upsertOrderBook(BITFINEX, BTC_USD, new OrderBook(new Date(), Arrays.asList(ask2), Arrays.asList(bid2)));
        Assert.assertEquals(1, oscillationArbitrager.getOrderBooks(BTC_USD).size());
    }

    @Test
    public void computeTrades() {
    }
}