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
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
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

    MetadataAggregator metadataAggregator;

    //Cached Info
    Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    private OpenOrdersParams openOrdersParamsAll;

    /**
     * Default Constructor for unit-testing
     */
    public CoinbaseProExchangeRestAPI() { }

    public CoinbaseProExchangeRestAPI(Configuration cfg,
                                      MetadataAggregator metadataAggregator) throws IOException {
        if (cfg.getCoinbaseProConfig().isEnabled()) {
            ExchangeSpecification exSpec = new CoinbaseProExchange().getDefaultExchangeSpecification();
            exSpec.setSecretKey(cfg.getCoinbaseProConfig().getSecretKey());
            exSpec.setApiKey(cfg.getCoinbaseProConfig().getApiKey());
            exSpec.setExchangeSpecificParametersItem("passphrase", cfg.getCoinbaseProConfig().getPassphrase());

            this.metadataAggregator = metadataAggregator;

            exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (CoinbaseProAccountService)exchange.getAccountService();
            tradeService = (CoinbaseProTradeService)exchange.getTradeService();
            marketDataService = (CoinbaseProMarketDataService)exchange.getMarketDataService();

            openOrdersParamsAll = tradeService.createOpenOrdersParams();

            //Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.info("CoinbaseProRestAPI is disabled"); //TODO: Replace with exception?
        }
    }

    public void refreshProducts() throws IOException {
        exchange.remoteInit(); //A new reference will be saved in the exchange instance
        metadataMap = exchange.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(COINBASE_PRO, metadataMap);
    }

    public void refreshFees() throws IOException {
        feeMap = accountService.getDynamicTradingFees(); //A new reference will be returned here
        metadataAggregator.upsertFeeMap(COINBASE_PRO, feeMap);
    }

    public void refreshAccountInfo() throws IOException {
        accountInfo = accountService.getAccountInfo(); //A new reference will be returned here
        metadataAggregator.upsertAccountInfo(COINBASE_PRO, accountInfo);
    }

    public Balance getBalance(Currency currency) throws Exception {
        return accountInfo.getWallet().getBalance(currency);
    }

    /*
    public void submitBuyLimitOrder() throws Exception {
        return tradeService.placeLimitOrder(new LimitOrder());
    }

    public void submitSellLimitOrder() {

    }

     */
}
