package rest;

import config.Configuration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.service.BitfinexAccountService;
import org.knowm.xchange.bitfinex.service.BitfinexMarketDataService;
import org.knowm.xchange.bitfinex.service.BitfinexTradeService;
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

import static constants.Exchange.BITFINEX;

public class BitfinexExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(BitfinexExchangeRestAPI.class);

    private Exchange exchange;
    private BitfinexAccountService accountService;
    private BitfinexTradeService tradeService;
    private BitfinexMarketDataService marketDataService;

    private MetadataAggregator metadataAggregator;

    //Cached Info
    Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    public BitfinexExchangeRestAPI(Configuration cfg,
                                   MetadataAggregator metadataAggregator) throws IOException {
        if (cfg.getBitfinexConfig().isEnabled()) {
            ExchangeSpecification exSpec = new BitfinexExchange().getDefaultExchangeSpecification();

            exSpec.setSecretKey(cfg.getBitfinexConfig().getSecretKey());
            exSpec.setApiKey(cfg.getBitfinexConfig().getApiKey());

            exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (BitfinexAccountService)exchange.getAccountService();
            tradeService = (BitfinexTradeService)exchange.getTradeService();
            marketDataService = (BitfinexMarketDataService)exchange.getMarketDataService();

            this.metadataAggregator = metadataAggregator;

            //Get status details
            /*
            for (Bitfinex product : marketDataService.getStatus()) {
                LOG.info(product.toString());
            }

             */

            //Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.info("BitfinexRestAPI is disabled"); //TODO: Replace with exception?
        }
    }

    public void refreshProducts() throws IOException {
        metadataMap = exchange.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(BITFINEX, metadataMap);
    }

    public void refreshFees() throws IOException {
        feeMap = accountService.getDynamicTradingFees();
        metadataAggregator.upsertFeeMap(BITFINEX, feeMap);
    }

    public void refreshAccountInfo() throws IOException {
        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(BITFINEX, accountInfo);
    }

    public Map<CurrencyPair, Fee> getFees() throws Exception {
        return feeMap;
    }

    public Balance getBalance(Currency currency) throws Exception {
        return accountInfo.getWallet().getBalance(currency);
    }
}
