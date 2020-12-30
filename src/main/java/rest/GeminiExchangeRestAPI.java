package rest;

import config.Configuration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.gemini.v1.GeminiExchange;
import org.knowm.xchange.gemini.v1.service.GeminiAccountService;
import org.knowm.xchange.gemini.v1.service.GeminiMarketDataService;
import org.knowm.xchange.gemini.v1.service.GeminiTradeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.Map;

import static constants.Exchange.GEMINI;

public class GeminiExchangeRestAPI implements ExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(GeminiExchangeRestAPI.class);

    private final constants.Exchange exchangeName = GEMINI;
    private Exchange exchangeInstance;
    private GeminiAccountService accountService;
    private GeminiTradeService tradeService;
    private GeminiMarketDataService marketDataService;

    private MetadataAggregator metadataAggregator;

    //Cached Info
    Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    public GeminiExchangeRestAPI(Configuration cfg,
                                      MetadataAggregator metadataAggregator) throws IOException {
        if (cfg.getGeminiConfig().isEnabled()) {
            ExchangeSpecification exSpec = new GeminiExchange().getDefaultExchangeSpecification();
            exSpec.setApiKey(cfg.getGeminiConfig().getApiKey());
            exSpec.setSecretKey(cfg.getGeminiConfig().getSecretKey());

            exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (GeminiAccountService)exchangeInstance.getAccountService();
            tradeService = (GeminiTradeService)exchangeInstance.getTradeService();
            marketDataService = (GeminiMarketDataService)exchangeInstance.getMarketDataService();

            this.metadataAggregator = metadataAggregator;

            //Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.info("GeminiRestAPI is disabled"); //TODO: Replace with exception?
        }
    }

    @Override
    public constants.Exchange getExchangeName() {
        return exchangeName;
    }

    @Override
    public void refreshProducts() throws IOException {
        exchangeInstance.remoteInit();
        metadataMap = exchangeInstance.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(GEMINI, metadataMap);
    }

    @Override
    public void refreshFees() throws IOException {
        feeMap = accountService.getDynamicTradingFees();
        metadataAggregator.upsertFeeMap(GEMINI, feeMap);
    }

    @Override
    public void refreshAccountInfo() throws IOException {
        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(GEMINI, accountInfo);
    }

    public Map<CurrencyPair, Fee> getFees() throws Exception {
        return feeMap;
    }

    public Balance getBalance(Currency currency) throws Exception {
        return accountInfo.getWallet().getBalance(currency);
    }
}
