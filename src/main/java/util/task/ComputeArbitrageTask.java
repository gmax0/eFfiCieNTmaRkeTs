package util.task;

import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;
import services.arbitrage.SpatialArbitrager;

import java.math.BigDecimal;
import java.util.TreeSet;

/**
 * To-Implement
 */
public class ComputeArbitrageTask implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ComputeArbitrageTask.class);

  private SpatialArbitrager spatialArbitrager;
  private TreeSet<SpatialArbitrager.Entry<Exchange, OrderBook>> ascendingAsks;
  private TreeSet<SpatialArbitrager.Entry<Exchange, OrderBook>> descendingBids;
  private CurrencyPair currencyPair;
  private MetadataAggregator metadataAggregator;

  public ComputeArbitrageTask(
      SpatialArbitrager spatialArbitrager,
      TreeSet<SpatialArbitrager.Entry<Exchange, OrderBook>> ascendingAsks,
      TreeSet<SpatialArbitrager.Entry<Exchange, OrderBook>> descendingBids,
      CurrencyPair currencyPair,
      MetadataAggregator metadataAggregator) {
    this.spatialArbitrager = spatialArbitrager;
    this.ascendingAsks = ascendingAsks;
    this.descendingBids = descendingBids;
    this.currencyPair = currencyPair;
    this.metadataAggregator = metadataAggregator;
  }

  @Override
  public void run() {
    try {
      LOG.debug("Running computeTrades()");
      computeTrades();
      LOG.debug("Completed computeTrades()");
    } catch (Exception e) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), e);
    }
  }

  private void matchOrders() {}

  private void computeTrades() {
    BigDecimal minGain = spatialArbitrager.getMinGain();

    SpatialArbitrager.Entry<Exchange, OrderBook>[] ascendingAsksArr =
        (SpatialArbitrager.Entry<Exchange, OrderBook>[]) ascendingAsks.toArray();
    SpatialArbitrager.Entry<Exchange, OrderBook>[] descendingBidsArr =
        (SpatialArbitrager.Entry<Exchange, OrderBook>[]) descendingBids.toArray();

    for (SpatialArbitrager.Entry<Exchange, OrderBook> askOrderBook : ascendingAsksArr) {

      Integer ex1PriceScale = metadataAggregator.getPriceScale(askOrderBook.getKey(), currencyPair);
      BigDecimal ex1MinOrderAmount =
          metadataAggregator.getMinimumOrderAmount(askOrderBook.getKey(), currencyPair);
      BigDecimal ex1MaxOrder;
      Fee ex1Fees = metadataAggregator.getFees(askOrderBook.getKey(), currencyPair);
      BigDecimal ex1MakerFee = ex1Fees.getMakerFee();
      BigDecimal ex1TakerFee = ex1Fees.getTakerFee();

      for (SpatialArbitrager.Entry<Exchange, OrderBook> bidOrderBook : descendingBidsArr) {
        if (askOrderBook.getKey().equals(bidOrderBook.getKey())) continue;

        Integer ex2PriceScale =
            metadataAggregator.getPriceScale(bidOrderBook.getKey(), currencyPair);
        BigDecimal ex2MinOrderAmount =
            metadataAggregator.getMinimumOrderAmount(bidOrderBook.getKey(), currencyPair);
        BigDecimal ex2MaxOrder;
        Fee ex2Fees = metadataAggregator.getFees(bidOrderBook.getKey(), currencyPair);
        BigDecimal ex2MakerFee = ex2Fees.getMakerFee();
        BigDecimal ex2TakerFee = ex2Fees.getTakerFee();
      }
    }
  }
}
