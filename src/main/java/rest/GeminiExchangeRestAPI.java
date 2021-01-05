package rest;

import config.Configuration;
import domain.Trade;
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

import static domain.constants.Exchange.GEMINI;

public class GeminiExchangeRestAPI implements ExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(GeminiExchangeRestAPI.class);

    private final domain.constants.Exchange exchangeName = GEMINI;
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
            LOG.info("Initializing {}ExchangeRestAPI.", exchangeName);

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
            LOG.info("{}RestAPI is disabled", exchangeName); //TODO: Replace with exception?
        }
    }

    @Override
    public domain.constants.Exchange getExchangeName() {
        return exchangeName;
    }

    @Override
    public void refreshProducts() throws IOException {
        LOG.info("Refreshing {} Product Info.", exchangeName);

        exchangeInstance.remoteInit();
        metadataMap = exchangeInstance.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(GEMINI, metadataMap);

        LOG.debug(metadataMap.toString());
    }

    @Override
    public void refreshFees() throws IOException {
        LOG.info("Refreshing {} Fee Info.", exchangeName);

        feeMap = accountService.getDynamicTradingFees();
        metadataAggregator.upsertFeeMap(GEMINI, feeMap);

        LOG.debug(feeMap.toString());
    }

    @Override
    public void refreshAccountInfo() throws IOException {
        LOG.info("Refreshing {} Account Info.", exchangeName);

        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(GEMINI, accountInfo);

        LOG.debug(accountInfo.toString());
    }

    @Override
    public String submitTrade(Trade trade) throws IOException {
        LOG.info("Submitting Trade: {}", trade);
        switch (trade.getOrderType()) {
            case STOP:
                return tradeService.placeStopOrder(trade.toStopOrder());
            case LIMIT:
                return tradeService.placeLimitOrder(trade.toLimitOrder());
            case MARKET:
                return tradeService.placeMarketOrder(trade.toMarketOrder());
        }
        LOG.warn("Trade order type not supported: {}", trade.getOrderType());
        return null;
    }
}
