package services.arbitrage;

import buffer.TradeBuffer;
import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import config.Configuration;
import domain.Trade;
import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;
import util.ThreadFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static domain.constants.OrderType.LIMIT;
import static org.knowm.xchange.dto.Order.OrderType.ASK;
import static org.knowm.xchange.dto.Order.OrderType.BID;

/** A basic, naive spatial arbitrage algorithm for initial testing purposes. */
public class SpatialArbitrager implements EventHandler<OrderBookEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(SpatialArbitrager.class);

  // Sort OrderBooks in ascending order according to each book's first ask
  private static final Comparator<Map.Entry<Exchange, OrderBook>> ascendingAskComparator =
      (e1, e2) -> {
        if (e1.getKey().name().equals(e2.getKey().name())) return 0;
        return e1.getValue().getAsks().get(0).compareTo(e2.getValue().getAsks().get(0));
      };

  // Sort OrderBooks in descending order according to each book's first bid
  private static final Comparator<Map.Entry<Exchange, OrderBook>> descendingBidComparator =
      (e1, e2) -> {
        if (e1.getKey().name().equals(e2.getKey().name())) return 0;
        return e1.getValue().getBids().get(0).compareTo(e2.getValue().getBids().get(0));
      };

  private MetadataAggregator metadataAggregator;
  private TradeBuffer tradeBuffer;
  private ExecutorService executorService;

  private final Map<CurrencyPair, TreeSet<Entry<Exchange, OrderBook>>> orderBooksAscendingAsks =
      new ConcurrentHashMap<>();
  private final Map<CurrencyPair, TreeSet<Entry<Exchange, OrderBook>>> orderBooksDescendingBids =
      new ConcurrentHashMap<>();

  private BigDecimal minGain;

  public SpatialArbitrager(
      Configuration cfg, MetadataAggregator metadataAggregator, TradeBuffer tradeBuffer) {
    this.minGain = cfg.getSpatialArbitragerConfig().getMinGain();

    this.metadataAggregator = metadataAggregator;
    this.tradeBuffer = tradeBuffer;
    executorService = Executors.newFixedThreadPool(3, new ThreadFactory("V1WorkerPool"));
  }

  public class Entry<K, V> implements Map.Entry<K, V> {
    private final K key;
    private V value;

    public Entry(final K key) {
      this.key = key;
    }

    public Entry(final K key, final V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(final V value) {
      final V oldValue = this.value;
      this.value = value;
      return oldValue;
    }
  }

  public BigDecimal getMinGain() {
    return this.minGain;
  }

  public TreeSet<Entry<Exchange, OrderBook>> getOrderBooksAscendingAsks(CurrencyPair currencyPair) {
    return orderBooksAscendingAsks.get(currencyPair);
  }

  public TreeSet<Entry<Exchange, OrderBook>> getOrderBooksDescendingBids(
      CurrencyPair currencyPair) {
    return orderBooksDescendingBids.get(currencyPair);
  }

  @Override
  public void onEvent(OrderBookEvent event, long sequence, boolean endOfBatch) {
    this.upsertOrderBook(event.exchange, event.currencyPair, event.orderBook);
  }

  // TODO: Not sure why but long-running executions results in a single exchange with a duplicate
  // entry in the TreeSet...
  public void upsertOrderBook(Exchange exchange, CurrencyPair currencyPair, OrderBook orderBook) {
    // Update TreeSets
    orderBooksAscendingAsks.computeIfAbsent(
        currencyPair,
        (k) -> {
          return new TreeSet(ascendingAskComparator);
        });
    orderBooksDescendingBids.computeIfAbsent(
        currencyPair,
        (k) -> {
          return new TreeSet(descendingBidComparator);
        });

    Entry<Exchange, OrderBook> entry = new Entry(exchange, orderBook);
    if (orderBooksAscendingAsks.get(currencyPair).contains(entry)) {
      orderBooksAscendingAsks.get(currencyPair).remove(entry);
    }
    if (orderBooksDescendingBids.get(currencyPair).contains(entry)) {
      orderBooksDescendingBids.get(currencyPair).remove(entry);
    }
    orderBooksAscendingAsks.get(currencyPair).add(entry);
    orderBooksDescendingBids.get(currencyPair).add(entry);

    // Perform Checks
    if (orderBooksAscendingAsks.size() != orderBooksDescendingBids.size()) {
      LOG.error("Different TreeSet sizes for currency pair {}", currencyPair);
      return;
    }
    // Not enough exchanges to analyze price deviations
    if (orderBooksAscendingAsks.get(currencyPair).size() <= 1) {
      LOG.debug(
          "Currency Pair: {} does not possess the minimum number of exchanges to perform spatial arbitrage analysis",
          currencyPair);
      return;
    }

    processOrderbooks(currencyPair);

    // Submit Task
    /*
    executorService.submit(new ComputeArbitrageTask(
            this,
            (TreeSet<Entry<Exchange, OrderBook>>) orderBooksAscendingAsks.get(currencyPair).clone(),
            (TreeSet<Entry<Exchange, OrderBook>>) orderBooksDescendingBids.get(currencyPair).clone(),
            currencyPair,
            metadataAggregator
    ));
     */
  }

  /**
   * TODO: Add user enable-able logic to place a maker order on either buy/sell side (in the spread
   * zone)
   *
   * @param currencyPair
   * @param askOrderBook
   * @param bidOrderBook
   * @return
   */
  private boolean extractTrades(
      CurrencyPair currencyPair,
      Entry<Exchange, OrderBook> askOrderBook,
      Entry<Exchange, OrderBook> bidOrderBook) {
    Integer ex1PriceScale = metadataAggregator.getPriceScale(askOrderBook.getKey(), currencyPair);
    BigDecimal ex1MinOrderAmount =
        metadataAggregator.getMinimumOrderAmount(askOrderBook.getKey(), currencyPair);
    BigDecimal ex1MaxOrder;
    Fee ex1Fees = metadataAggregator.getFees(askOrderBook.getKey(), currencyPair);
    BigDecimal ex1MakerFee = ex1Fees.getMakerFee();
    BigDecimal ex1TakerFee = ex1Fees.getTakerFee();

    Integer ex2PriceScale = metadataAggregator.getPriceScale(bidOrderBook.getKey(), currencyPair);
    BigDecimal ex2MinOrderAmount =
        metadataAggregator.getMinimumOrderAmount(bidOrderBook.getKey(), currencyPair);
    BigDecimal ex2MaxOrder;
    Fee ex2Fees = metadataAggregator.getFees(bidOrderBook.getKey(), currencyPair);
    BigDecimal ex2MakerFee = ex2Fees.getMakerFee();
    BigDecimal ex2TakerFee = ex2Fees.getTakerFee();

    // To determine price level ceiling for a maker order on ex1
    BigDecimal ex1HighestBid = askOrderBook.getValue().getBids().get(0).getLimitPrice();
    // To determine price level floor for a maker order on ex2
    BigDecimal ex2LowestAsk = bidOrderBook.getValue().getAsks().get(0).getLimitPrice();

    boolean tradesDiscovered = true; // Set to true so that the initial iteration may occur
    List<LimitOrder> asks = askOrderBook.getValue().getAsks();
    List<LimitOrder> bids = bidOrderBook.getValue().getBids();
    List<LimitOrder> consumedAsks = new ArrayList<>();
    List<LimitOrder> consumedBids = new ArrayList<>();
    for (int i = 0; i < asks.size(); i++) {
      if (!tradesDiscovered) break;

      BigDecimal ex1CurLowestAskPrice = asks.get(i).getLimitPrice();
      BigDecimal ex1CurLowestAskVolume = asks.get(i).getOriginalAmount();

      // Min-volume Check for ex1
      if (ex1CurLowestAskVolume.compareTo(ex1MinOrderAmount) < 0) continue;

      for (int j = 0; j < bids.size(); j++) {
        BigDecimal ex2CurHighestBidPrice = bids.get(j).getLimitPrice();
        BigDecimal ex2CurHighestBidVolume = bids.get(j).getOriginalAmount();

        // Min-volume Check for ex2
        if (ex2CurHighestBidVolume.compareTo(ex2MinOrderAmount) < 0) continue;

        BigDecimal effectiveBaseOrderVolume = ex1CurLowestAskVolume.min(ex2CurHighestBidVolume);

        // Check for costToBuy = 0: .02371 * .0000004 / (1 - .0026)
        // Prices AND Fees are assumed to be in the quote currency (See README for details)
        BigDecimal costToBuy = ex1CurLowestAskPrice.multiply(effectiveBaseOrderVolume);
        BigDecimal totalCostToBuy = costToBuy.add(costToBuy.multiply(ex1TakerFee));

        BigDecimal incomeSold = ex2CurHighestBidPrice.multiply(effectiveBaseOrderVolume);
        BigDecimal totalIncomeSold = incomeSold.subtract(incomeSold.multiply(ex2TakerFee));

        if (totalIncomeSold.compareTo(totalCostToBuy) > 0
            && totalIncomeSold
                    .subtract(totalCostToBuy)
                    .divide(totalCostToBuy, 5, RoundingMode.HALF_EVEN)
                    .compareTo(minGain)
                >= 0) {
          Trade buyLow =
              Trade.builder()
                  .exchange(askOrderBook.getKey())
                  .currencyPair(currencyPair)
                  .orderActionType(BID)
                  .orderType(LIMIT)
                  .price(ex1CurLowestAskPrice)
                  .amount(effectiveBaseOrderVolume)
                  .timeDiscovered(Instant.now())
                  .fee(ex1TakerFee)
                  .build();
          Trade sellHigh =
              Trade.builder()
                  .exchange(bidOrderBook.getKey())
                  .currencyPair(currencyPair)
                  .orderActionType(ASK)
                  .orderType(LIMIT)
                  .price(ex2CurHighestBidPrice)
                  .amount(effectiveBaseOrderVolume)
                  .timeDiscovered(Instant.now())
                  .fee(ex2TakerFee)
                  .build();
          LOG.info(
              "Arbitrage Opportunity Detected for {} ! Buy {} units on {} at {}, Sell {} units on {} at {}",
              currencyPair,
              effectiveBaseOrderVolume,
              askOrderBook.getKey(),
              ex1CurLowestAskPrice,
              effectiveBaseOrderVolume,
              bidOrderBook.getKey(),
              ex2CurHighestBidPrice);
          LOG.info(
              "With fees calculated, Cost To Buy: {} , Amount Sold: {}, Profit: {}",
              totalCostToBuy,
              totalIncomeSold,
              totalIncomeSold.subtract(totalCostToBuy));
          tradeBuffer.insert(buyLow, sellHigh);

          tradesDiscovered = true;
          consumedAsks.add(asks.get(i));
          consumedBids.add(bids.get(j));
        } else {
          // No other opportunities can possibly exist
          tradesDiscovered = false;
          break;
        }
      }
    }
    asks.removeAll(consumedAsks);
    bids.removeAll(consumedBids);

    return consumedAsks.isEmpty() && consumedBids.isEmpty();
  }

  /**
   * Iterative Approach to computing arbitrage opportunities.
   *
   * @param currencyPair
   */
  public void processOrderbooks(CurrencyPair currencyPair) {
    try {
      Entry<Exchange, OrderBook>[] ascendingAsksArr =
          new Entry[orderBooksAscendingAsks.get(currencyPair).size()];
      Entry<Exchange, OrderBook>[] descendingBidsArr =
          new Entry[orderBooksDescendingBids.get(currencyPair).size()];
      ascendingAsksArr = orderBooksAscendingAsks.get(currencyPair).toArray(ascendingAsksArr);
      descendingBidsArr = orderBooksDescendingBids.get(currencyPair).toArray(descendingBidsArr);

      boolean tradesPublished = false;
      for (SpatialArbitrager.Entry<Exchange, OrderBook> askOrderBook : ascendingAsksArr) {
        for (SpatialArbitrager.Entry<Exchange, OrderBook> bidOrderBook : descendingBidsArr) {

          // If no orders were submitted for the current bidOrderBook, there's no need
          // for further processing of bidOrderBooks against the current askOrderBook
          tradesPublished = extractTrades(currencyPair, askOrderBook, bidOrderBook);
          if (!tradesPublished) break;
        }
      }
    } catch (Exception e) {
      LOG.error("Exception caught while performing computation for {}", currencyPair, e);
    }
  }
}
