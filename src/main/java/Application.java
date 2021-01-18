import buffer.OrderBookBuffer;
import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import rest.*;
import services.BalanceCaptor;
import services.TradePublisher;
import services.arbitrage.SpatialArbitragerV2;
import streams.BitfinexExchangeStream;
import streams.CoinbaseProExchangeStream;
import streams.GeminiExchangeStream;
import streams.KrakenExchangeStream;
import streams.*;
import util.ThreadFactory;
import buffer.TradeBuffer;
import config.Configuration;
import config.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.task.BalanceCaptorTask;
import util.task.RestAPIRefreshTask;
import services.MetadataAggregator;
import services.arbitrage.SpatialArbitrager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Application {
  public static Logger LOG = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws Exception {
    // Setup Configs
    ConfigurationManager configurationManager =
        new ConfigurationManager(Application.class.getResourceAsStream("config.yaml"));
    Configuration config = configurationManager.getConfig();

    Map<Exchange, List<CurrencyPair>> activeExchangesPairMap =
        configurationManager.getActiveExchangesPairMap();
    LOG.info("Active Exchanges: {}", activeExchangesPairMap.keySet());

    // Setup Auxillary Services
    // TODO: setup a dependency injection framework
    MetadataAggregator metadataAggregator = new MetadataAggregator();
    BalanceCaptor balanceCaptor = new BalanceCaptor(metadataAggregator, activeExchangesPairMap);

    // Setup ExchangeRestAPIs
    GeminiExchangeRestAPI geminiExchangeRestAPI =
        new GeminiExchangeRestAPI(config, metadataAggregator);
    CoinbaseProExchangeRestAPI coinbaseProExchangeRestAPI =
        new CoinbaseProExchangeRestAPI(config, metadataAggregator);
    BitfinexExchangeRestAPI bitfinexExchangeRestAPI =
        new BitfinexExchangeRestAPI(config, metadataAggregator);
    KrakenExchangeRestAPI krakenExchangeRestAPI =
        new KrakenExchangeRestAPI(config, metadataAggregator);
    BinanceExchangeRestAPI binanceExchangeRestAPI =
        new BinanceExchangeRestAPI(config, metadataAggregator);
    CexExchangeRestAPI cexExchangeRestAPI = new CexExchangeRestAPI(config, metadataAggregator);

    TradePublisher tradePublisher =
        new TradePublisher(
            metadataAggregator,
            geminiExchangeRestAPI,
            coinbaseProExchangeRestAPI,
            bitfinexExchangeRestAPI,
            krakenExchangeRestAPI);
    TradeBuffer tradeBuffer = new TradeBuffer(tradePublisher);
    SpatialArbitrager spatialArbitrager =
        new SpatialArbitrager(config, metadataAggregator, tradeBuffer);
    SpatialArbitragerV2 spatialArbitragerV2 =
            new SpatialArbitragerV2(config, metadataAggregator, tradeBuffer);
    OrderBookBuffer orderBookBuffer = new OrderBookBuffer(spatialArbitrager, spatialArbitragerV2);

    // Start Buffers
    tradeBuffer.start();
    orderBookBuffer.start();

    Thread.sleep(1000);

    // Setup Recurring Tasks
    RestAPIRefreshTask geminiAPIRefreshTask = new RestAPIRefreshTask(geminiExchangeRestAPI);
    RestAPIRefreshTask coinbaseAPIRefreshTask = new RestAPIRefreshTask(coinbaseProExchangeRestAPI);
    RestAPIRefreshTask bitfinexAPIRefreshTask = new RestAPIRefreshTask(bitfinexExchangeRestAPI);
    RestAPIRefreshTask krakenAPIRefreshTask = new RestAPIRefreshTask(krakenExchangeRestAPI);
    RestAPIRefreshTask binanceAPIRefreshTask = new RestAPIRefreshTask(binanceExchangeRestAPI);
    RestAPIRefreshTask cexAPIRefreshTask = new RestAPIRefreshTask(cexExchangeRestAPI);
    ScheduledExecutorService scheduledExecutorService =
        Executors.newScheduledThreadPool(1, new ThreadFactory("RecurringTasks"));
    scheduledExecutorService.scheduleAtFixedRate(
        new BalanceCaptorTask(balanceCaptor), 0, 60, TimeUnit.SECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
        geminiAPIRefreshTask, 0, config.getGeminiConfig().getRefreshRate(), TimeUnit.SECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
        coinbaseAPIRefreshTask,
        0,
        config.getCoinbaseProConfig().getRefreshRate(),
        TimeUnit.SECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
        bitfinexAPIRefreshTask, 0, config.getBitfinexConfig().getRefreshRate(), TimeUnit.SECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
        krakenAPIRefreshTask, 0, config.getKrakenConfig().getRefreshRate(), TimeUnit.SECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
        binanceAPIRefreshTask, 0, config.getBinanceConfig().getRefreshRate(), TimeUnit.SECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
        cexAPIRefreshTask, 0, config.getBinanceConfig().getRefreshRate(), TimeUnit.SECONDS);

    // Setup WebSocket Streams
    GeminiExchangeStream geminiExchangeStream = new GeminiExchangeStream(config, orderBookBuffer);
    geminiExchangeStream.start();
    KrakenExchangeStream krakenExchangeStream = new KrakenExchangeStream(config, orderBookBuffer);
    krakenExchangeStream.start();
    CoinbaseProExchangeStream coinbaseProExchangeStream =
        new CoinbaseProExchangeStream(config, orderBookBuffer);
    coinbaseProExchangeStream.start();
    BitfinexExchangeStream bitfinexExchangeStream =
        new BitfinexExchangeStream(config, orderBookBuffer);
    bitfinexExchangeStream.start();
    BinanceExchangeStream binanceExchangeStream =
        new BinanceExchangeStream(config, orderBookBuffer);
    binanceExchangeStream.start();
    CexExchangeStream cexExchangeStream = new CexExchangeStream(config, orderBookBuffer);
    cexExchangeStream.start();

    // Setup Shutdown Hook, TODO: clean this up later
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  balanceCaptor.refreshBalances();
                  balanceCaptor.captureBalances();
                }));

    while (true) {
      Thread.sleep(Long.MAX_VALUE);
    }

    /*
    //Real-Time Chart Testing
    XYChart chart = new XYChartBuilder().width(800).height(600).title("CoinbasePro Order Book").xAxisTitle("USD").yAxisTitle("BTC").build();
    chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Area);

    OrderBook orderBook = bookkeeper.getOrderBook(GEMINI, CurrencyPair.BTC_USD);
    // BIDS
    List<Number> xData = new ArrayList<>();
    List<Number> yData = new ArrayList<>();
    BigDecimal accumulatedBidUnits = new BigDecimal("0");
    for (LimitOrder limitOrder : orderBook.getBids()) {
            xData.add(limitOrder.getLimitPrice());
            accumulatedBidUnits = accumulatedBidUnits.add(limitOrder.getOriginalAmount());
            yData.add(accumulatedBidUnits);
    }
    Collections.reverse(xData);
    Collections.reverse(yData);

    // Bids Series
    XYSeries series = chart.addSeries("bids", xData, yData);
    series.setMarker(SeriesMarkers.NONE);

    // ASKS
    xData = new ArrayList<>();
    yData = new ArrayList<>();
    BigDecimal accumulatedAskUnits = new BigDecimal("0");
    for (LimitOrder limitOrder : orderBook.getAsks()) {
            xData.add(limitOrder.getLimitPrice());
            accumulatedAskUnits = accumulatedAskUnits.add(limitOrder.getOriginalAmount());
            yData.add(accumulatedAskUnits);
    }

    // Asks Series
    series = chart.addSeries("asks", xData, yData);
    series.setMarker(SeriesMarkers.NONE);
    final SwingWrapper<XYChart> sw = new SwingWrapper<>(chart);
    sw.displayChart();

    while (true) {
        Thread.sleep(1000);
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                OrderBook orderBook = bookkeeper.getOrderBook(GEMINI, CurrencyPair.BTC_USD);
                // BIDS
                List<Number> xData = new ArrayList<>();
                List<Number> yData = new ArrayList<>();
                BigDecimal accumulatedBidUnits = new BigDecimal("0");
                for (LimitOrder limitOrder : orderBook.getBids()) {
                    xData.add(limitOrder.getLimitPrice());
                    accumulatedBidUnits = accumulatedBidUnits.add(limitOrder.getOriginalAmount());
                    yData.add(accumulatedBidUnits);
                }
                Collections.reverse(xData);
                Collections.reverse(yData);

                // Bids Series
                XYSeries series = chart.updateXYSeries("bids", xData, yData, null);
                series.setMarker(SeriesMarkers.NONE);

                // ASKS
                xData = new ArrayList<>();
                yData = new ArrayList<>();
                BigDecimal accumulatedAskUnits = new BigDecimal("0");
                for (LimitOrder limitOrder : orderBook.getAsks()) {
                    xData.add(limitOrder.getLimitPrice());
                    accumulatedAskUnits = accumulatedAskUnits.add(limitOrder.getOriginalAmount());
                    yData.add(accumulatedAskUnits);
                }

                // Asks Series
                series = chart.updateXYSeries("asks", xData, yData, null);
                series.setMarker(SeriesMarkers.NONE);

                sw.repaintChart();
            }
        });
    }
    /*
    bookkeeper.shutdown();
    scheduledExecutorService.shutdown();
    LOG.info("END");

     */
  }
}
