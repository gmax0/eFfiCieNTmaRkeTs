package services;

import domain.Trade;
import domain.constants.Exchange;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rest.GeminiExchangeRestAPI;
import rest.KrakenExchangeRestAPI;
import testUtils.MetadataAggregatorMocker;

import java.math.BigDecimal;

import static domain.constants.Exchange.GEMINI;
import static domain.constants.Exchange.KRAKEN;
import static org.junit.Assert.*;
import static org.knowm.xchange.currency.Currency.BTC;
import static org.knowm.xchange.currency.Currency.USD;
import static org.knowm.xchange.currency.CurrencyPair.BTC_USD;
import static org.knowm.xchange.dto.Order.OrderType.ASK;
import static org.knowm.xchange.dto.Order.OrderType.BID;
import static org.mockito.Mockito.when;

public class TradePublisherTest {

    @Mock
    private MetadataAggregator metadataAggregator;
    @Mock
    private KrakenExchangeRestAPI krakenExchangeRestAPI;
    @Mock
    private GeminiExchangeRestAPI geminiExchangeRestAPI;

    TradePublisher tradePublisher;


    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(krakenExchangeRestAPI.getExchange()).thenReturn(KRAKEN);
        when(geminiExchangeRestAPI.getExchange()).thenReturn(GEMINI);

        tradePublisher = new TradePublisher(metadataAggregator, krakenExchangeRestAPI, geminiExchangeRestAPI);
    }

    @Test
    public void test_1() {
        MetadataAggregatorMocker.setMockBalance(metadataAggregator, KRAKEN, USD, new BigDecimal(29000));
        MetadataAggregatorMocker.setMockBalance(metadataAggregator, GEMINI, BTC, new BigDecimal(1));

        MetadataAggregatorMocker.setMockPriceScale(metadataAggregator, KRAKEN, BTC_USD, 1);
        MetadataAggregatorMocker.setMockPriceScale(metadataAggregator, GEMINI, BTC_USD, 1);

        MetadataAggregatorMocker.setOrderMinimumVolume(metadataAggregator, KRAKEN, BTC_USD, new BigDecimal(0.001));
        MetadataAggregatorMocker.setOrderMinimumVolume(metadataAggregator, GEMINI, BTC_USD, new BigDecimal(0.00000001));

        Trade trade1 = Trade.builder()
                .exchange(KRAKEN)
                .orderActionType(BID)
                .currencyPair(BTC_USD)
                .price(new BigDecimal(30000))
                .amount(new BigDecimal(1))
                .fee(new BigDecimal(0.05))
                .build();

        Trade trade2 = Trade.builder()
                .exchange(GEMINI)
                .orderActionType(ASK)
                .currencyPair(BTC_USD)
                .price(new BigDecimal(31000))
                .amount(new BigDecimal(1))
                .fee(new BigDecimal(0.05))
                .build();

        tradePublisher.processSpatialArbitrageTrade(trade1, trade2);
    }

    @Test
    public void test_2() {
        /*
        setMockBalance(KRAKEN);
        setMockBalance(GEMINI);

         */
    }
}