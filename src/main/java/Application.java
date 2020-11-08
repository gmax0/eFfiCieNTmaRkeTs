import buffer.OrderBookBuffer;
import buffer.handlers.OrderBookEventHandler;
import config.Configuration;
import config.CustomConversionHandler;
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
import rest.CoinbaseProExchangeRestAPI;
import services.Bookkeeper;
import streams.CoinbaseProExchangeStream;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static constants.Exchange.COINBASE_PRO;

public class Application {
    public static Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        //Test out Configuration Management
        YAMLConfiguration yamlConfiguration = new YAMLConfiguration();
        yamlConfiguration.setConversionHandler(new CustomConversionHandler());
        yamlConfiguration.read(Application.class.getResourceAsStream("config.yaml"));
        yamlConfiguration.getKeys().forEachRemaining(k -> {
            LOG.info(k);
        });
        Configuration config = Configuration.builder()
                .coinbaseProConfig(Configuration.CoinbaseProConfig.builder()
                        .enabled(yamlConfiguration.getBoolean("exchange.coinbase_pro.enabled"))
                        .apiKey(yamlConfiguration.getString("exchange.coinbase_pro.api.credentials.api_key"))
                        .secretKey(yamlConfiguration.getString("exchange.coinbase_pro.api.credentials.secret_key"))
                        .passphrase(yamlConfiguration.getString("exchange.coinbase_pro.api.credentials.passphrase"))
                        .currencyPairs(yamlConfiguration.getList(CurrencyPair.class, "exchange.coinbase_pro.websocket.currency_pairs"))
                        .build())
                .build();

        LOG.info(Boolean.toString(config.getCoinbaseProConfig().isEnabled()));
        LOG.info(config.getCoinbaseProConfig().getApiKey());
        LOG.info(config.getCoinbaseProConfig().getSecretKey());
        LOG.info(config.getCoinbaseProConfig().getPassphrase());
        LOG.info(config.getCoinbaseProConfig().getCurrencyPairs().toString());


        CoinbaseProExchangeRestAPI coinbaseProExchangeRestAPI = new CoinbaseProExchangeRestAPI(config);

        //TODO: setup a dependency injection framework
        Bookkeeper bookkeeper = new Bookkeeper();
        OrderBookBuffer orderBookBuffer = new OrderBookBuffer(new OrderBookEventHandler(bookkeeper));
        orderBookBuffer.start();

        CoinbaseProExchangeStream coinbaseProExchangeStream = new CoinbaseProExchangeStream(config, orderBookBuffer,
                Arrays.asList(CurrencyPair.BTC_USD, CurrencyPair.ETH_USD), 10);
        coinbaseProExchangeStream.start();

        Thread.sleep(2000);

        //Real-Time Chart Testing
        XYChart chart = new XYChartBuilder().width(800).height(600).title("CoinbasePro Order Book").xAxisTitle("USD").yAxisTitle("BTC").build();
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Area);

        OrderBook orderBook = bookkeeper.getOrderBook(COINBASE_PRO, CurrencyPair.BTC_USD);
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
                    OrderBook orderBook = bookkeeper.getOrderBook(COINBASE_PRO, CurrencyPair.BTC_USD);
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
