package rest;

import config.Configuration;
import domain.constants.Exchange;
import domain.constants.OrderType;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProOrderFlags;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.Set;

import static domain.constants.Exchange.COINBASE_PRO;

public class CoinbaseProExchangeRestAPI extends AbstractExchangeRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeRestAPI.class);
  private final Exchange exchange = COINBASE_PRO;

  @Override
  Logger getLog() {
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

  LimitOrder customizeLimitOrder(LimitOrder limitOrder, OrderType orderType) {
    switch (orderType) {
      case LIMIT_FOK:
        limitOrder.setOrderFlags(Set.of(CoinbaseProOrderFlags.FILL_OR_KILL));
        break;
      case LIMIT_IOC:
        limitOrder.setOrderFlags(Set.of(CoinbaseProOrderFlags.IMMEDIATE_OR_CANCEL));
        break;
      case LIMIT_MAKER_ONLY:
        //Not valid when combined with either FOK or IOC
        limitOrder.setOrderFlags(Set.of(CoinbaseProOrderFlags.POST_ONLY));
        break;
      default:
        break;
    }
    return limitOrder;
  }
}
