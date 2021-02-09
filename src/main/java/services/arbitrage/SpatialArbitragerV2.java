package services.arbitrage;

import buffer.TradeBuffer;
import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import config.Configuration;
import domain.ExchangeLimitOrder;
import domain.Trade;
import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;
import util.ThreadFactory;
import util.task.ComputeArbitrageTaskV2;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/** To-Implement: Aggregate all asks and bids into respective, sorted arrays. */
public class SpatialArbitragerV2 implements EventHandler<OrderBookEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(SpatialArbitragerV2.class);

  public static final Comparator<ExchangeLimitOrder> ascendingAskComparator =
          Comparator.comparing(e -> e.getLimitOrder().getLimitPrice());
  public static final Comparator<ExchangeLimitOrder> descendingBidComparator =
          Comparator.comparing(e -> e.getLimitOrder().getLimitPrice(), Comparator.reverseOrder());

  private MetadataAggregator metadataAggregator;
  private TradeBuffer tradeBuffer;
  private ExecutorService executorService;

  private final Map<CurrencyPair, ArrayList<ExchangeLimitOrder>> aggregatedBids = new ConcurrentHashMap<>();
  private final Map<CurrencyPair, ArrayList<ExchangeLimitOrder>> aggregatedAsks = new ConcurrentHashMap<>();

  private final Map<CurrencyPair, Map<Exchange, OrderBook>> orderBooksAll = new ConcurrentHashMap<>();

  private final Map<CurrencyPair, Map<Exchange, Set<LimitOrder>>> consumedBids =
          new HashMap<>();
  private final Map<CurrencyPair, Map<Exchange, Set<LimitOrder>>> consumedAsks =
          new HashMap<>();

  private BigDecimal minGain;

  public SpatialArbitragerV2(Configuration cfg,
                             MetadataAggregator metadataAggregator,
                             TradeBuffer tradeBuffer) {
    this.minGain = new BigDecimal("0.001");
    this.metadataAggregator = metadataAggregator;
    this.tradeBuffer = tradeBuffer;
    executorService = Executors.newFixedThreadPool(5, new ThreadFactory("V2WorkerPool"));
  }

  public BigDecimal getMinGain() {
    return this.minGain;
  }

  public void processOrderBook(Exchange exchange, CurrencyPair currencyPair, OrderBook orderBook) {
    orderBooksAll.computeIfAbsent(
        currencyPair,
        (k) -> {
          return new ConcurrentHashMap<>();
        });
    aggregatedAsks.computeIfAbsent(
        currencyPair,
        (k) -> {
          return new ArrayList<>();
        });
    aggregatedBids.computeIfAbsent(
        currencyPair,
        (k) -> {
          return new ArrayList<>();
        });

    orderBooksAll.get(currencyPair).put(exchange, orderBook);

    // Remove all orders matching the exchange
    aggregatedBids.get(currencyPair).removeIf(o -> o.getExchange().equals(exchange));
    aggregatedAsks.get(currencyPair).removeIf(o -> o.getExchange().equals(exchange));

    // Reinsert
    aggregatedAsks
        .get(currencyPair)
        .addAll(
            orderBook.getAsks().stream()
                .map(ask -> ExchangeLimitOrder.builder().exchange(exchange).limitOrder(ask).build())
                .collect(Collectors.toList()));
    aggregatedBids
        .get(currencyPair)
        .addAll(
            orderBook.getBids().stream()
                .map(bid -> ExchangeLimitOrder.builder().exchange(exchange).limitOrder(bid).build())
                .collect(Collectors.toList()));
  }

  @Override
  public void onEvent(OrderBookEvent event, long sequence, boolean endOfBatch) {
    //Update the real-time books
    this.processOrderBook(event.exchange, event.currencyPair, event.orderBook);

    //Submit Task
    executorService.submit(new ComputeArbitrageTaskV2(
            this,
            (ArrayList)aggregatedAsks.get(event.currencyPair).clone(),
            (ArrayList)aggregatedBids.get(event.currencyPair).clone(),
            event.currencyPair,
            metadataAggregator
            ));
    LOG.debug("Worker Pool Active Count: {}", ((ThreadPoolExecutor)executorService).getActiveCount());
  }

  private boolean tradeExists(Trade trade) {
    OrderBook orderBook = orderBooksAll.get(trade.getCurrencyPair()).get(trade.getExchange());
    if (orderBook == null) {
      LOG.info("Orderbook was null");
      return false;
    }

    LimitOrder limitOrder;

    if (trade.getOrderActionType().equals(Order.OrderType.ASK)) {
      //If order type is ASK, search for the bid that we're fulfilling
      LOG.info("Checking for bid...");
      limitOrder = orderBook.getBids().stream().filter(bids -> bids.getLimitPrice().equals(trade.getPrice())).findFirst().orElse(null);
    } else {
      //If order type is BID, search for the ask that we're fulfilling
      LOG.info("Checking for ask...");
      limitOrder = orderBook.getAsks().stream().filter(asks -> asks.getLimitPrice().equals(trade.getPrice())).findFirst().orElse(null);
    }
    LOG.info("{}", limitOrder);

    if (limitOrder != null && limitOrder.getOriginalAmount().compareTo(trade.getAmount()) >= 0) {
      LOG.info("Returning true");
      return true;
    }
    LOG.info("Returning false");
    return false;
  }

  public void callback(Trade bid, Trade ask) {
    //Check that the order has not already been executed on

    //Check that the opportunity still exists
    if (tradeExists(bid) && tradeExists(ask)) {
      //Execute Order
      LOG.info("Submitting Bid Trade: {}", bid);
      LOG.info("Submitting Ask Trade: {}", ask);
      tradeBuffer.insert(bid, ask);
    } else {
      LOG.info("Trades no longer exist!");
    }

    //Mark
  }

}
