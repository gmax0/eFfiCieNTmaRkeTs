package rest;

import config.Configuration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.coinbasepro.service.CoinbaseProAccountService;
import org.knowm.xchange.coinbasepro.service.CoinbaseProMarketDataService;
import org.knowm.xchange.coinbasepro.service.CoinbaseProTradeService;
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

import static constants.Exchange.COINBASE_PRO;


public class CoinbaseProExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeRestAPI.class);

    private Exchange exchange;
    private CoinbaseProAccountService accountService;
    private CoinbaseProTradeService tradeService;
    private CoinbaseProMarketDataService marketDataService;

    private MetadataAggregator metadataAggregator;

    //Cached Info
    Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    public CoinbaseProExchangeRestAPI(Configuration cfg,
                                      MetadataAggregator metadataAggregator) throws IOException {
        if (cfg.getCoinbaseProConfig().isEnabled()) {
            ExchangeSpecification exSpec = new CoinbaseProExchange().getDefaultExchangeSpecification();
            exSpec.setSecretKey(cfg.getCoinbaseProConfig().getSecretKey());
            exSpec.setApiKey(cfg.getCoinbaseProConfig().getApiKey());
            exSpec.setExchangeSpecificParametersItem("passphrase", cfg.getCoinbaseProConfig().getPassphrase());

            exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (CoinbaseProAccountService)exchange.getAccountService();
            tradeService = (CoinbaseProTradeService)exchange.getTradeService();
            marketDataService = (CoinbaseProMarketDataService)exchange.getMarketDataService();

            this.metadataAggregator = metadataAggregator;


            //Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.info("CoinbaseProRestAPI is disabled"); //TODO: Replace with exception?
        }
    }

    public void refreshProducts() throws IOException {
        metadataMap = exchange.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(COINBASE_PRO, metadataMap);
    }

    public void refreshFees() throws IOException {
        feeMap = accountService.getDynamicTradingFees();
        metadataAggregator.upsertFeeMap(COINBASE_PRO, feeMap);
    }

    public void refreshAccountInfo() throws IOException {
        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(COINBASE_PRO, accountInfo);
    }

    public Map<CurrencyPair, Fee> getFees() throws Exception {
        return feeMap;
    }

    public Balance getBalance(Currency currency) throws Exception {
        return accountInfo.getWallet().getBalance(currency);
    }
}
