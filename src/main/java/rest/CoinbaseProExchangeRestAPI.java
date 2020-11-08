package rest;

import config.Configuration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class CoinbaseProExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeRestAPI.class);

    private Exchange exchange;
    private AccountService accountService;
    private TradeService tradeService;

    //Cached Info
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    public CoinbaseProExchangeRestAPI(Configuration cfg) throws IOException {
        if (cfg.getCoinbaseProConfig().isEnabled()) {
            ExchangeSpecification exSpec = new CoinbaseProExchange().getDefaultExchangeSpecification();
            exSpec.setSecretKey(cfg.getCoinbaseProConfig().getSecretKey());
            exSpec.setApiKey(cfg.getCoinbaseProConfig().getApiKey());
            exSpec.setExchangeSpecificParametersItem("passphrase", cfg.getCoinbaseProConfig().getPassphrase());

            exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = exchange.getAccountService();
            tradeService = exchange.getTradeService();

            //Cache initial calls
            accountInfo = accountService.getAccountInfo();
            feeMap = accountService.getDynamicTradingFees();
        } else {
            LOG.info("CoinbaseProRestAPI is disabled"); //TODO: Replace with exception?
        }
    }

    public Map<CurrencyPair, Fee> getFees() throws Exception {
        return feeMap;
    }

    public Balance getBalance(Currency currency) throws Exception {
        return accountInfo.getWallet().getBalance(currency);
    }
}
