package services;

import buffer.TradeBuffer;
import config.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import services.arbitrage.SpatialArbitrager;
import testUtils.OrderBookProvider;

import java.math.BigDecimal;
import java.util.Date;

import static domain.constants.Exchange.BITFINEX;
import static domain.constants.Exchange.COINBASE_PRO;
import static org.knowm.xchange.currency.CurrencyPair.BTC_USD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpatialArbitragerTest {

    @Spy
    Configuration mockConfig = Configuration.builder()
            .spatialArbitragerConfig(Configuration.SpatialArbitragerConfig.builder()
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
        OrderBook orderBook1 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date1, "orderBookData/BTC-0-bids.csv", "orderBookData/BTC-0-asks.csv");
        OrderBook orderBook2 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date2, "orderBookData/BTC-0-bids.csv", "orderBookData/BTC-0-asks.csv");
        OrderBook orderBook3 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date3, "orderBookData/BTC-0-bids.csv", "orderBookData/BTC-0-asks.csv");

        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook1);
        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook2);
        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook3);

        //Assert.assertEquals(1, spatialArbitrager.getOrderBooks(BTC_USD).size()); //TreeSet should contain only 1 entry for BITFINEX
        //Assert.assertEquals(date3, spatialArbitrager.getOrderBooks(BTC_USD).pollFirst().getValue().getTimeStamp());
    }

    /**
     *
     */
    @Test
    public void computeTrades_1() {
        Date date = new Date();
        OrderBook orderBook1 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date, "orderBookData/custom/CUSTOM-1-bids.csv", "orderBookData/custom/CUSTOM-1-asks.csv");
        OrderBook orderBook2 = OrderBookProvider.getOrderBookFromCSV(BTC_USD, date, "orderBookData/custom/CUSTOM-2-bids.csv", "orderBookData/custom/CUSTOM-2-asks.csv");

        when(mockMetadataAggregator.getMakerFee(any(), any())).thenReturn(BigDecimal.ZERO);
        when(mockMetadataAggregator.getTakerFee(any(), any())).thenReturn(BigDecimal.ZERO);

        spatialArbitrager.upsertOrderBook(BITFINEX, BTC_USD, orderBook1);
        spatialArbitrager.upsertOrderBook(COINBASE_PRO, BTC_USD, orderBook2); //computeTrades() should occur on this call
    }
}