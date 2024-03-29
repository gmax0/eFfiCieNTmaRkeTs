package rest;

import config.Configuration;
import domain.constants.Exchange;
import domain.constants.OrderType;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;

import static domain.constants.Exchange.BITFINEX;

public class BitfinexExchangeRestAPI extends AbstractExchangeRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(BitfinexExchangeRestAPI.class);
  private final Exchange exchange = BITFINEX;

  @Override
  Logger getLog() {
    return LOG;
  }

  @Override
  public Exchange getExchange() {
    return this.exchange;
  }

  public BitfinexExchangeRestAPI(Configuration cfg, MetadataAggregator metadataAggregator)
      throws IOException {
    if (cfg.getBitfinexConfig().isEnabled()) {
      LOG.info("Initializing {}ExchangeRestAPI.", exchange);
      this.isEnabled = true;

      ExchangeSpecification exSpec = new BitfinexExchange().getDefaultExchangeSpecification();

      exSpec.setSecretKey(cfg.getBitfinexConfig().getSecretKey());
      exSpec.setApiKey(cfg.getBitfinexConfig().getApiKey());

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
      LOG.warn("{}ExchangeRestAPI is diabled", exchange);
    }
  }

  // TODO: Add advance limit order params
  LimitOrder customizeLimitOrder(LimitOrder limitOrder, OrderType orderType) {
    return limitOrder;
  }
}
