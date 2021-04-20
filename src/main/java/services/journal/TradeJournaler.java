package services.journal;

import config.Configuration;
import domain.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TradeJournaler {
  private static final Logger LOG = LogManager.getLogger(TradeJournaler.class);

  private final boolean dbEnabled;

  public TradeJournaler(Configuration config) {
    dbEnabled = config.getApplicationConfig().getJournalerConfig().isEnabled();
  }

  public void logDetectedTrade(Trade trade1, Trade trade2) {
    LOG.info(
        "Spatial Arbitrage Opportunity Detected for {} ! Buy {} units on {} at {}, Sell {} units on {} at {}",
        trade1.getCurrencyPair(),
        trade1.getAmount(),
        trade1.getExchange(),
        trade1.getPrice(),
        trade2.getAmount(),
        trade2.getExchange(),
        trade2.getPrice());
    LOG.info(
        "With fees calculated, Cost To Buy: {} , Amount Sold: {}, Profit: {}",
        trade1.getTotal(),
        trade2.getTotal(),
        trade2.getTotal().subtract(trade1.getTotal()));

    if (dbEnabled) {
      // TODO: Save event to database
    }
  }

  public void logSubmittedTrade(Trade trade) {
    LOG.info(
        "Submitting Trade: {} {} {} {} {} {} {}",
        trade.getExchange(),
        trade.getCurrencyPair(),
        trade.getOrderActionType(),
        trade.getOrderType(),
        trade.getPrice(),
        trade.getAmount(),
        trade.getTimeDiscovered());
    if (dbEnabled) {
      // TODO: Save event to database
    }
  }

  public void logCompletedTrade() {}
}
