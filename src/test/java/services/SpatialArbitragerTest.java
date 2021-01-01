package services;

import buffer.TradeBuffer;
import config.Configuration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import testUtils.OrderBookProvider;

import java.math.BigDecimal;
import java.util.Date;

import static constants.Exchange.BITFINEX;
import static org.knowm.xchange.currency.CurrencyPair.BTC_USD;

@RunWith(MockitoJUnitRunner.class)
public class SpatialArbitragerTest {

    @Spy
    Configuration mockConfig = Configuration.builder()
            .oscillationArbitragerConfig(Configuration.OscillationArbitragerConfig.builder()
                    .minGain(new BigDecimal(.001))
                    .build())
            .build();
    @Mock
    MetadataAggregator mockMetadataAggregator;
    @Mock
    TradeBuffer tradeBuffer;
    @InjectMocks
    SpatialArbitrager spatialArbitrager;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void onEvent() {
    }

    @Test
    public void upsertOrderBook_testForDuplicates_updatedWithLatest() {
        Date date1 = new Date();
        Date date2 = new Date();
        Date date3 = new Date();
        OrderBook orderBook1 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date1, "services/BTC-1-bids.csv", "services/BTC-1-asks.csv");
        OrderBook orderBook2 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date2, "services/BTC-1-bids.csv", "services/BTC-1-asks.csv");
        OrderBook orderBook3 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date3, "services/BTC-1-bids.csv", "services/BTC-1-asks.csv");

        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook1);
        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook2);
        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook3);
        Assert.assertEquals(1, spatialArbitrager.getOrderBooks(BTC_USD).size()); //TreeSet should contain only 1 entry for BITFINEX
        Assert.assertEquals(date3, spatialArbitrager.getOrderBooks(BTC_USD).pollFirst().getValue().getTimeStamp());
    }

    @Test
    public void computeTrades() {
    }
}