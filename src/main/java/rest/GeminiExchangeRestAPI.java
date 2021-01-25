package rest;

import config.Configuration;
import domain.constants.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.gemini.v1.GeminiExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;

import static domain.constants.Exchange.GEMINI;

public class GeminiExchangeRestAPI extends AbstractExchangeRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(GeminiExchangeRestAPI.class);
  private final domain.constants.Exchange exchange = GEMINI;

  @Override
  public Logger getLog() {
    return LOG;
  }

  @Override
  public Exchange getExchange() {
    return exchange;
  }

  public GeminiExchangeRestAPI(Configuration cfg, MetadataAggregator metadataAggregator)
      throws IOException {
    if (cfg.getGeminiConfig().isEnabled()) {
      LOG.info("Initializing {}ExchangeRestAPI.", exchange);
      this.isEnabled = true;

      ExchangeSpecification exSpec = new GeminiExchange().getDefaultExchangeSpecification();
      exSpec.setApiKey(cfg.getGeminiConfig().getApiKey());
      exSpec.setSecretKey(cfg.getGeminiConfig().getSecretKey());

      exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
      accountService = exchangeInstance.getAccountService();
      tradeService = exchangeInstance.getTradeService();
      marketDataService = exchangeInstance.getMarketDataService();

      this.metadataAggregator = metadataAggregator;

      // Cache initial calls
      refreshProducts();
      refreshFees();
      refreshAccountInfo();
    } else {
      LOG.warn("{}RestAPI is disabled", exchange);
    }
  }
}
