package rest;

import config.Configuration;
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

import static constants.Exchange.KRAKEN;

public class KrakenExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(KrakenExchangeRestAPI.class);

    private Exchange exchange;
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
            ExchangeSpecification exSpec = new KrakenExchange().getDefaultExchangeSpecification();

            exSpec.setSecretKey(cfg.getKrakenConfig().getSecretKey());
            exSpec.setApiKey(cfg.getKrakenConfig().getApiKey());

            exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (KrakenAccountService)exchange.getAccountService();
            tradeService = (KrakenTradeService)exchange.getTradeService();
            marketDataService = (KrakenMarketDataService)exchange.getMarketDataService();

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
            LOG.info("KrakenRestAPI is disabled"); //TODO: Replace with exception?
        }
    }

    public void refreshProducts() throws IOException {
        metadataMap = exchange.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees might be static for Kraken
        metadataAggregator.upsertMetadata(KRAKEN, metadataMap);
    }

    public void refreshFees() throws IOException {
//        feeMap = accountService.getDynamicTradingFees(); //TODO: XChange to implement getDynamicTradingFees
        if (feeMap == null) {
            feeMap = new HashMap<>();
        }
        //TODO: Double check whether the fees in the exchangeMetaData are accurate... 0.26% looks ok for now
        exchange.getExchangeMetaData().getCurrencyPairs().forEach((currencyPair, currencyPairMetaData) -> {
            feeMap.put(currencyPair, new Fee(currencyPairMetaData.getTradingFee(), currencyPairMetaData.getTradingFee()));
        });
        metadataAggregator.upsertFeeMap(KRAKEN, feeMap);
    }

    public void refreshAccountInfo() throws IOException {
        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(KRAKEN, accountInfo);
    }

    public Map<CurrencyPair, Fee> getFees() throws Exception {
        return feeMap;
    }

    public Balance getBalance(Currency currency) throws Exception {
        return accountInfo.getWallet().getBalance(currency);
    }
}
