package services;

import buffer.events.TradeEvent;
import com.lmax.disruptor.EventHandler;
import domain.Trade;
import domain.constants.Exchange;
import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rest.AbstractExchangeRestAPI;
import services.journal.TradeJournaler;
import util.ThreadFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TradePublisher implements EventHandler<TradeEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(TradePublisher.class);

  private MetadataAggregator metadataAggregator;
  private final Map<Exchange, AbstractExchangeRestAPI> exchangeRestAPIMap = new HashMap<>();
  private TradeJournaler tradeJournaler;
  private ExecutorService executorService;

  public TradePublisher() {}

  public TradePublisher(
      MetadataAggregator metadataAggregator,
      TradeJournaler tradeJournaler,
      AbstractExchangeRestAPI... abstractExchangeRestAPI) {
    this.metadataAggregator = metadataAggregator;
    this.tradeJournaler = tradeJournaler;

    // Load the Exchange -> ExchangeRestAPI map
    for (AbstractExchangeRestAPI exchangeRestAPI1 : abstractExchangeRestAPI) {
      if (exchangeRestAPI1.isEnabled()) {
        exchangeRestAPIMap.put(exchangeRestAPI1.getExchange(), exchangeRestAPI1);
      }
    }

    // Initialize Executor
    executorService =
        Executors.newFixedThreadPool(4, new ThreadFactory("trade-publisher-executor-service"));
  }

  @Override
  public void onEvent(TradeEvent event, long sequence, boolean endOfBatch) {
    Trade trade1 = event.getTrade1();
    Trade trade2 = event.getTrade2();
    Trade trade3 = event.getTrade3();

    if (trade3 == null) {
      if (trade1 != null && trade2 != null) {
        processSpatialArbitrageTrade(trade1, trade2);
      }
    } else {
        processTriangularArbitrage(trade1, trade2, trade3);
    }
  }

  // TODO: Perform scaling and minimum checks in the arbitrage layer instead...
  // Assumes that the Amount on both Trades are equivalent
  public BigDecimal calculateMaxActionableAmount(Trade t1, Trade t2) {
    // Example:
    // A bid for BTC/USD would require a check on the USD balance
    // An ask for BTC/USD would require a check on the BTC balance
    BigDecimal availableCounterBalance =
        metadataAggregator
            .getBalance(
                t1.getExchange(),
                t1.getOrderActionType().equals(Order.OrderType.BID)
                    ? t1.getCurrencyPair().counter
                    : t1.getCurrencyPair().base)
            .getAvailable();
    BigDecimal availableBaseBalance =
        metadataAggregator
            .getBalance(
                t2.getExchange(),
                t2.getOrderActionType().equals(Order.OrderType.BID)
                    ? t2.getCurrencyPair().counter
                    : t2.getCurrencyPair().base)
            .getAvailable();

    LOG.info(
        "Available counter ({}) balance on {} : {}, Available base ({}) balance on {} : {}",
        t1.getCurrencyPair().counter,
        t1.getExchange(),
        availableCounterBalance,
        t2.getCurrencyPair().base,
        t2.getExchange(),
        availableBaseBalance);

    // TODO: implement counterMaximum and baseMaximum if you become an arbitrage whale
    Integer counterPriceScale =
        metadataAggregator.getPriceScale(t1.getExchange(), t1.getCurrencyPair());
    BigDecimal ex1OrderMinimum =
        metadataAggregator.getMinimumOrderAmount(t1.getExchange(), t1.getCurrencyPair());

    Integer basePriceScale =
        metadataAggregator.getPriceScale(t2.getExchange(), t2.getCurrencyPair());
    BigDecimal ex2OrderMinimum =
        metadataAggregator.getMinimumOrderAmount(t2.getExchange(), t2.getCurrencyPair());

    BigDecimal maxBaseAmount =
        t1.getAmount(); // t1.getAmount() && t2.getAmount() are assumed equivalent
    BigDecimal reqCounterToBuy = t1.getPrice().multiply(maxBaseAmount);
    reqCounterToBuy =
        reqCounterToBuy.add(
            reqCounterToBuy.multiply(t1.getFee())); // Assumes fees are in counter currency

    if (availableCounterBalance.compareTo(reqCounterToBuy) >= 0
        && availableBaseBalance.compareTo(maxBaseAmount) >= 0) {
      maxBaseAmount =
          maxBaseAmount.setScale(
              Integer.min(counterPriceScale, basePriceScale), BigDecimal.ROUND_DOWN);
      LOG.info(
          "Base and counter balances are sufficient to buy and sell {} (scaled) of {}",
          maxBaseAmount,
          t1.getCurrencyPair());
    } else {
      LOG.info("Insufficient base or counter balances...calculating max actionable base amount...");
      BigDecimal maxBaseBuyable =
          (availableCounterBalance.subtract(availableCounterBalance.multiply(t1.getFee())))
              .divide(t1.getPrice(), counterPriceScale, BigDecimal.ROUND_DOWN);
      LOG.info("Max base buyable: {}", maxBaseBuyable);

      BigDecimal maxBaseSellable = availableBaseBalance.min(maxBaseAmount);
      LOG.info("Max base sellable: {}", maxBaseSellable);

      if (maxBaseBuyable.compareTo(ex1OrderMinimum) < 1) {
        LOG.info(
            "Max base buyable is less than minimum order volume: {} on exchange {}",
            ex1OrderMinimum,
            t1.getExchange());
        return BigDecimal.ZERO;
      }
      if (maxBaseSellable.compareTo(ex2OrderMinimum) < 1) {
        LOG.info(
            "Max base sellable is less than minimum order volume: {} on exchange {}",
            ex2OrderMinimum,
            t2.getExchange());
        return BigDecimal.ZERO;
      }

      maxBaseAmount = maxBaseBuyable.min(maxBaseSellable);
    }
    return maxBaseAmount;
  }

  /**
   * Submits both legs of a spatial arbitrage opportunity to their respective exchange.
   * @param trade1 - The BUY order of the opportunity
   * @param trade2 - The SELL order of the opportunity
   */
  private void processSpatialArbitrageTrade(Trade trade1, Trade trade2) {
    // Load exchangeRestAPIs for both Trades
    AbstractExchangeRestAPI exchangeRestAPI1 = exchangeRestAPIMap.get(trade1.getExchange());
    if (exchangeRestAPI1 == null) {
      LOG.error("Unable to load {}'s exchangeRestAPI", trade1.getExchange());
      return;
    }
    AbstractExchangeRestAPI exchangeRestAPI2 = exchangeRestAPIMap.get(trade2.getExchange());
    if (exchangeRestAPI2 == null) {
      LOG.error("Unable to load {}'s exchangeRestAPI", trade2.getExchange());
      return;
    }

    BigDecimal maxActionableAmount = calculateMaxActionableAmount(trade1, trade2);
    LOG.info("Max Actionable Amount: {}", maxActionableAmount);
    trade1.setAmount(maxActionableAmount);
    trade2.setAmount(maxActionableAmount);

    if (maxActionableAmount.compareTo(BigDecimal.ZERO) > 0) {
      tradeJournaler.logSubmittedTrade(trade1);
      executorService.execute(
          () -> {
            try {
              exchangeRestAPI1.submitTrade(trade1);
            } catch (IOException e) {
              LOG.error("Caught exception while executing order submission: {}", e);
              LOG.error("Extended Stack Trace: {}", e.getStackTrace());
            }
          });
      tradeJournaler.logSubmittedTrade(trade2);
      executorService.execute(
          () -> {
            try {
              exchangeRestAPI2.submitTrade(trade2);
            } catch (IOException e) {
              LOG.error("Caught exception while executing order submission: {}", e);
              LOG.error("Extended Stack Trace: {}", e.getStackTrace());
            }
          });
    }
  }

  private void processTriangularArbitrage(Trade trade1, Trade trade2, Trade trade3) {
    //TODO: To-implement
  }
}
