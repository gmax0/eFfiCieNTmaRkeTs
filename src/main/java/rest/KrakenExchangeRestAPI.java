package rest;

import config.Configuration;
import domain.Trade;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenMarketDataService;
import org.knowm.xchange.kraken.service.KrakenTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static domain.constants.Exchange.KRAKEN;

public class KrakenExchangeRestAPI implements ExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(KrakenExchangeRestAPI.class);

    private final domain.constants.Exchange exchangeName = KRAKEN;
    private Exchange exchangeInstance;
    private KrakenAccountService accountService;
    private KrakenTradeService tradeService;
    private KrakenMarketDataService marketDataService;

    private MetadataAggregator metadataAggregator;

    //Cached Info
    Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    public KrakenExchangeRestAPI(Configuration cfg,
                                   MetadataAggregator metadataAggregator) throws IOException {
        if (cfg.getKrakenConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeRestAPI.", exchangeName);

            ExchangeSpecification exSpec = new KrakenExchange().getDefaultExchangeSpecification();

            exSpec.setSecretKey(cfg.getKrakenConfig().getSecretKey());
            exSpec.setApiKey(cfg.getKrakenConfig().getApiKey());

            exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (KrakenAccountService)exchangeInstance.getAccountService();
            tradeService = (KrakenTradeService)exchangeInstance.getTradeService();
            marketDataService = (KrakenMarketDataService)exchangeInstance.getMarketDataService();

            this.metadataAggregator = metadataAggregator;

            //Get status details
            /*
            for (Kraken product : marketDataService.getStatus()) {
                LOG.info(product.toString());
            }

             */

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
        metadataMap = exchangeInstance.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees might be static for Kraken
        metadataAggregator.upsertMetadata(KRAKEN, metadataMap);

        LOG.debug(metadataMap.toString());
    }

    @Override
    public void refreshFees() throws IOException {
        LOG.info("Refreshing {} Fee Info.", exchangeName);

//        feeMap = accountService.getDynamicTradingFees(); //TODO: XChange to implement getDynamicTradingFees
        feeMap = new HashMap<>();
        exchangeInstance.remoteInit();
        //TODO: Double check whether the fees in the exchangeMetaData are accurate... 0.26% looks ok for now
        exchangeInstance.getExchangeMetaData().getCurrencyPairs().forEach((currencyPair, currencyPairMetaData) -> {
            feeMap.put(currencyPair, new Fee(currencyPairMetaData.getTradingFee(), currencyPairMetaData.getTradingFee()));
        });
        metadataAggregator.upsertFeeMap(KRAKEN, feeMap);

        LOG.debug(feeMap.toString());
    }

    @Override
    public void refreshAccountInfo() throws IOException {
        LOG.info("Refreshing {} Account Info.", exchangeName);

        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(KRAKEN, accountInfo);

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
