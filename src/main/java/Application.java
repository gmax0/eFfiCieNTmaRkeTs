import buffer.OrderBookBuffer;
import buffer.handlers.OrderBookEventHandler;
import com.lmax.disruptor.RingBuffer;
import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.classpath.ClasspathConfigurationSource;
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
        //TODO: setup a dependency injection framework
        Bookkeeper bookkeeper = new Bookkeeper();
        OrderBookBuffer orderBookBuffer = new OrderBookBuffer(new OrderBookEventHandler(bookkeeper));
        orderBookBuffer.start();

        CoinbaseProExchangeStream coinbaseProExchangeStream = new CoinbaseProExchangeStream(orderBookBuffer,
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
