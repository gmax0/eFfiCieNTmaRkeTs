package rest;

import config.Configuration;
import domain.constants.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;

import static domain.constants.Exchange.COINBASE_PRO;

public class CoinbaseProExchangeRestAPI extends AbstractExchangeRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeRestAPI.class);
  private final Exchange exchange = COINBASE_PRO;

  @Override
  public Logger getLog() {
    return LOG;
  }

  @Override
  public Exchange getExchange() {
    return exchange;
  }

  /** Default Constructor for unit-testing */
  public CoinbaseProExchangeRestAPI() {}

  public CoinbaseProExchangeRestAPI(Configuration cfg, MetadataAggregator metadataAggregator)
      throws IOException {
    if (cfg.getCoinbaseProConfig().isEnabled()) {
      LOG.info("Initializing {}ExchangeRestAPI.", exchange);
      this.isEnabled = true;

      ExchangeSpecification exSpec = new CoinbaseProExchange().getDefaultExchangeSpecification();
      exSpec.setSecretKey(cfg.getCoinbaseProConfig().getSecretKey());
      exSpec.setApiKey(cfg.getCoinbaseProConfig().getApiKey());
      exSpec.setExchangeSpecificParametersItem(
          "passphrase", cfg.getCoinbaseProConfig().getPassphrase());

      this.metadataAggregator = metadataAggregator;

      exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
      accountService = exchangeInstance.getAccountService();
      tradeService = exchangeInstance.getTradeService();
      marketDataService = exchangeInstance.getMarketDataService();

      // Cache initial calls
      refreshProducts();
      refreshFees();
      refreshAccountInfo();
    } else {
      LOG.warn("{}RestAPI is disabled", exchange);
    }
  }
}
