package rest;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbasepro.service.CoinbaseProAccountService;
import org.knowm.xchange.coinbasepro.service.CoinbaseProMarketDataService;
import org.knowm.xchange.coinbasepro.service.CoinbaseProTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import services.MetadataAggregator;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CoinbaseProExchangeRestAPITest {
    @Mock
    Exchange exchange;
    @Mock
    ExchangeMetaData exchangeMetaData;
    @Mock
    CoinbaseProAccountService accountService;
    @Mock
    CoinbaseProTradeService tradeService;
    @Mock
    CoinbaseProMarketDataService marketDataService;
    @Spy
    private MetadataAggregator metadataAggregator = new MetadataAggregator();

    @InjectMocks
    CoinbaseProExchangeRestAPI coinbaseProExchangeRestAPI = new CoinbaseProExchangeRestAPI();

    @Before
    public void setup() throws Exception {
        openMocks(this);
    }

    @Test
    public void refreshProducts_currencyPairMetaDataMap_updatesByRef_onlyCalledOnce() throws Exception {
        Map<CurrencyPair, CurrencyPairMetaData> currencyPairCurrencyPairMetaDataMap = new HashMap<>();
        currencyPairCurrencyPairMetaDataMap.put(
                CurrencyPair.BTC_USD, new CurrencyPairMetaData(null, null, null, 0, null));

        when(exchange.getExchangeMetaData()).thenReturn(exchangeMetaData);
        when(exchangeMetaData.getCurrencyPairs()).thenReturn(currencyPairCurrencyPairMetaDataMap);

        //First Refresh Call
        coinbaseProExchangeRestAPI.refreshProducts();
        currencyPairCurrencyPairMetaDataMap.put(
                CurrencyPair.ETH_USD, new CurrencyPairMetaData(null, null, null, 0, null));

        //Second Refresh Call
        coinbaseProExchangeRestAPI.refreshProducts();

        verify(metadataAggregator, times(1)).upsertMetadata(eq(constants.Exchange.COINBASE_PRO), any());
        assertEquals(2, metadataAggregator.getCurrencyPairMetaDataMap(constants.Exchange.COINBASE_PRO).size());
    }
}