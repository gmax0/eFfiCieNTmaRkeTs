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

public class CoinbaseProExchangeRestAPI implements ExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeRestAPI.class);

    private final constants.Exchange exchangeName = COINBASE_PRO;
    private Exchange exchangeInstance;
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
            LOG.info("Initializing {}ExchangeRestAPI.", exchangeName);

            ExchangeSpecification exSpec = new CoinbaseProExchange().getDefaultExchangeSpecification();
            exSpec.setSecretKey(cfg.getCoinbaseProConfig().getSecretKey());
            exSpec.setApiKey(cfg.getCoinbaseProConfig().getApiKey());
            exSpec.setExchangeSpecificParametersItem("passphrase", cfg.getCoinbaseProConfig().getPassphrase());

            this.metadataAggregator = metadataAggregator;

            exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (CoinbaseProAccountService)exchangeInstance.getAccountService();
            tradeService = (CoinbaseProTradeService)exchangeInstance.getTradeService();
            marketDataService = (CoinbaseProMarketDataService)exchangeInstance.getMarketDataService();

            openOrdersParamsAll = tradeService.createOpenOrdersParams();

            //Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.info("{}RestAPI is disabled", exchangeName); //TODO: Replace with exception?
        }
    }

    @Override
    public constants.Exchange getExchangeName() {
        return exchangeName;
    }

    @Override
    public void refreshProducts() throws IOException {
        LOG.info("Refreshing {} Product Info.", exchangeName);

        exchangeInstance.remoteInit(); //A new reference will be saved in the exchangeInstance instance
        metadataMap = exchangeInstance.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(COINBASE_PRO, metadataMap);

        LOG.debug(metadataMap.toString());
    }

    @Override
    public void refreshFees() throws IOException {
        LOG.info("Refreshing {} Fee Info.", exchangeName);

        feeMap = accountService.getDynamicTradingFees(); //A new reference will be returned here
        metadataAggregator.upsertFeeMap(COINBASE_PRO, feeMap);

        LOG.debug(feeMap.toString());
    }

    @Override
    public void refreshAccountInfo() throws IOException {
        LOG.info("Refreshing {} Account Info.", exchangeName);

        accountInfo = accountService.getAccountInfo(); //A new reference will be returned here
        metadataAggregator.upsertAccountInfo(COINBASE_PRO, accountInfo);

        LOG.debug(accountInfo.toString());
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
