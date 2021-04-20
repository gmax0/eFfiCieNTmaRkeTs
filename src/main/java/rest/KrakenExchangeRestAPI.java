package rest;

import config.Configuration;
import domain.constants.Exchange;
import domain.constants.OrderType;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.gemini.v1.dto.trade.GeminiOrderFlags;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.dto.trade.KrakenOrderFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.Set;

import static domain.constants.Exchange.KRAKEN;

public class KrakenExchangeRestAPI extends AbstractExchangeRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(KrakenExchangeRestAPI.class);
  private final domain.constants.Exchange exchange = KRAKEN;

  @Override
  Logger getLog() {
    return LOG;
  }

  @Override
  public Exchange getExchange() {
    return exchange;
  }

  public KrakenExchangeRestAPI(Configuration cfg, MetadataAggregator metadataAggregator)
      throws IOException {
    if (cfg.getKrakenConfig().isEnabled()) {
      LOG.info("Initializing {}ExchangeRestAPI.", exchange);
      isEnabled = true;

      ExchangeSpecification exSpec = new KrakenExchange().getDefaultExchangeSpecification();

      exSpec.setSecretKey(cfg.getKrakenConfig().getSecretKey());
      exSpec.setApiKey(cfg.getKrakenConfig().getApiKey());

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
      case LIMIT_MAKER_ONLY:
        limitOrder.setOrderFlags(Set.of(GeminiOrderFlags.POST_ONLY));
        break;
      default:
        break;
    }
    return limitOrder;
  }
}
