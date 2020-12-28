import buffer.OrderBookBuffer;
import buffer.TradeBuffer;
import config.Configuration;
import config.CustomConversionHandler;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.kraken.KrakenStreamingExchange;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rest.BitfinexExchangeRestAPI;
import rest.CoinbaseProExchangeRestAPI;
import rest.GeminiExchangeRestAPI;
import rest.KrakenExchangeRestAPI;
import services.Bookkeeper;
import services.MetadataAggregator;
import services.OscillationArbitrager;
import streams.BitfinexExchangeStream;
import streams.CoinbaseProExchangeStream;
import streams.GeminiExchangeStream;
import streams.KrakenExchangeStream;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static constants.Exchange.BITFINEX;

public class Application {
    public static Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        //Setup Configs
        YAMLConfiguration yamlConfiguration = new YAMLConfiguration();
        yamlConfiguration.setConversionHandler(new CustomConversionHandler());
        yamlConfiguration.read(Application.class.getResourceAsStream("config.yaml"));
        yamlConfiguration.getKeys().forEachRemaining(k -> {
            LOG.debug(k);
        });
        Configuration config = Configuration.builder()
                .oscillationArbitragerConfig(Configuration.OscillationArbitragerConfig.builder()
                        .enabled(yamlConfiguration.getBoolean("strategies.oscillation.enabled"))
                        .minGain(yamlConfiguration.getBigDecimal("strategies.oscillation.min_gain"))
                        .build())
                .coinbaseProConfig(Configuration.CoinbaseProConfig.builder()
                        .enabled(yamlConfiguration.getBoolean("exchange.coinbase_pro.enabled"))
                        .apiKey(yamlConfiguration.getString("exchange.coinbase_pro.api.credentials.api_key"))
                        .secretKey(yamlConfiguration.getString("exchange.coinbase_pro.api.credentials.secret_key"))
                        .passphrase(yamlConfiguration.getString("exchange.coinbase_pro.api.credentials.passphrase"))
                        .currencyPairs(yamlConfiguration.getList(CurrencyPair.class, "exchange.coinbase_pro.websocket.currency_pairs"))
                        .depth(yamlConfiguration.getInt("exchange.coinbase_pro.websocket.depth"))
                        .build())
                .bitfinexConfig(Configuration.BitfinexConfig.builder()
                        .enabled(yamlConfiguration.getBoolean("exchange.bitfinex.enabled"))
                        .apiKey(yamlConfiguration.getString("exchange.bitfinex.api.credentials.api_key"))
                        .secretKey(yamlConfiguration.getString("exchange.bitfinex.api.credentials.secret_key"))
                        .currencyPairs(yamlConfiguration.getList(CurrencyPair.class, "exchange.bitfinex.websocket.currency_pairs"))
                        .depth(yamlConfiguration.getInt("exchange.bitfinex.websocket.depth"))
                        .build())
                .krakenConfig(Configuration.KrakenConfig.builder()
                        .enabled(yamlConfiguration.getBoolean("exchange.kraken.enabled"))
                        .apiKey(yamlConfiguration.getString("exchange.kraken.api.credentials.api_key"))
                        .secretKey(yamlConfiguration.getString("exchange.kraken.api.credentials.secret_key"))
                        .currencyPairs(yamlConfiguration.getList(CurrencyPair.class, "exchange.kraken.websocket.currency_pairs"))
                        .depth(yamlConfiguration.getInt("exchange.kraken.websocket.depth"))
                        .build())
                .geminiConfig(Configuration.GeminiConfig.builder()
                        .enabled(yamlConfiguration.getBoolean("exchange.gemini.enabled"))
                        .apiKey(yamlConfiguration.getString("exchange.gemini.api.credentials.api_key"))
                        .secretKey(yamlConfiguration.getString("exchange.gemini.api.credentials.secret_key"))
                        .currencyPairs(yamlConfiguration.getList(CurrencyPair.class, "exchange.gemini.websocket.currency_pairs"))
                        .depth(yamlConfiguration.getInt("exchange.gemini.websocket.depth"))
                        .build())
                .build();

        LOG.info(Boolean.toString(config.getCoinbaseProConfig().isEnabled()));
        LOG.info(config.getCoinbaseProConfig().getApiKey());
        LOG.info(config.getCoinbaseProConfig().getSecretKey());
        LOG.info(config.getCoinbaseProConfig().getPassphrase());
        LOG.info(config.getCoinbaseProConfig().getCurrencyPairs().toString());
        LOG.info(config.getBitfinexConfig().getApiKey());

        //Setup Services and Buffers
        //TODO: setup a dependency injection framework
        MetadataAggregator metadataAggregator = new MetadataAggregator();
        TradeBuffer tradeBuffer = new TradeBuffer();
        Bookkeeper bookkeeper = new Bookkeeper();
        OscillationArbitrager oscillationArbitrager = new OscillationArbitrager(config, metadataAggregator, tradeBuffer);
        OrderBookBuffer orderBookBuffer = new OrderBookBuffer(bookkeeper, oscillationArbitrager);
        orderBookBuffer.start();

        //Setup Publishers
        GeminiExchangeRestAPI geminiExchangeRestAPI = new GeminiExchangeRestAPI(config, metadataAggregator);
        CoinbaseProExchangeRestAPI coinbaseProExchangeRestAPI = new CoinbaseProExchangeRestAPI(config, metadataAggregator);
        //BitfinexExchangeRestAPI bitfinexExchangeRestAPI = new BitfinexExchangeRestAPI(config, metadataAggregator);
        //KrakenExchangeRestAPI krakenExchangeRestAPI = new KrakenExchangeRestAPI(config, metadataAggregator);

        //Setup ThreadExecutors
        /*
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    coinbaseProExchangeRestAPI.refreshProducts();
                    coinbaseProExchangeRestAPI.refreshAccountInfo();
                    coinbaseProExchangeRestAPI.refreshFees();

                    bitfinexExchangeRestAPI.refreshProducts();
                    bitfinexExchangeRestAPI.refreshAccountInfo();
                    bitfinexExchangeRestAPI.refreshFees();
                } catch (Exception e) {
                    Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

         */
//        scheduledExecutorService.shutdown();

        GeminiExchangeStream geminiExchangeStream = new GeminiExchangeStream(config, orderBookBuffer);
        geminiExchangeStream.start();
        KrakenExchangeStream krakenExchangeStream = new KrakenExchangeStream(config, orderBookBuffer);
        krakenExchangeStream.start();
        CoinbaseProExchangeStream coinbaseProExchangeStream = new CoinbaseProExchangeStream(config, orderBookBuffer);
        coinbaseProExchangeStream.start();
        BitfinexExchangeStream bitfinexExchangeStream = new BitfinexExchangeStream(config, orderBookBuffer);
        bitfinexExchangeStream.start();
        Thread.sleep(2000);


        //Real-Time Chart Testing
        XYChart chart = new XYChartBuilder().width(800).height(600).title("CoinbasePro Order Book").xAxisTitle("USD").yAxisTitle("BTC").build();
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Area);

        OrderBook orderBook = bookkeeper.getOrderBook(BITFINEX, CurrencyPair.BTC_USD);
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
                    OrderBook orderBook = bookkeeper.getOrderBook(BITFINEX, CurrencyPair.BTC_USD);
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
        LOG.info("END");

         */
    }
}
