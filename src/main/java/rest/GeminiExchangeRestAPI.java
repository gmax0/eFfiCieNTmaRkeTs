package rest;

import config.Configuration;
import domain.constants.Exchange;
import domain.constants.OrderType;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProOrderFlags;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.gemini.v1.GeminiExchange;
import org.knowm.xchange.gemini.v1.dto.trade.GeminiOrderFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.Set;

import static domain.constants.Exchange.GEMINI;

public class GeminiExchangeRestAPI extends AbstractExchangeRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(GeminiExchangeRestAPI.class);
  private final domain.constants.Exchange exchange = GEMINI;

  @Override
  Logger getLog() {
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

  LimitOrder customizeLimitOrder(LimitOrder limitOrder, OrderType orderType) {
    switch (orderType) {
      case LIMIT_FOK:
        limitOrder.setOrderFlags(Set.of(GeminiOrderFlags.FILL_OR_KILL));
        break;
      case LIMIT_IOC:
        limitOrder.setOrderFlags(Set.of(GeminiOrderFlags.IMMEDIATE_OR_CANCEL));
        break;
      case LIMIT_MAKER_ONLY:
        //Not valid when combined with either FOK or IOC
        limitOrder.setOrderFlags(Set.of(GeminiOrderFlags.POST_ONLY));
        break;
      default:
        break;
    }
    return limitOrder;
  }
}
