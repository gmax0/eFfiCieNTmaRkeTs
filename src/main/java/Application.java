import com.lmax.disruptor.RingBuffer;
import common.config.BookkeeperConfig;
import common.dto.OrderBookEvent;
import info.bitrich.xchangestream.coinbasepro.CoinbaseProStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.ExchangeSpecification;
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
import service.Bookkeeper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static common.dto.Exchange.COINBASE_PRO;

public class Application {
    public static Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        Bookkeeper bookkeeper = Bookkeeper.builder()
                .cfg(BookkeeperConfig.builder().bufferSize(1024).build())
                .build();
        bookkeeper.start();

        RingBuffer<OrderBookEvent> ringBuffer = bookkeeper.getRingBuffer();


        //XChange Test Code
        ExchangeSpecification coinbaseProSpec = new CoinbaseProStreamingExchange().getDefaultExchangeSpecification();

        ProductSubscription productSubscription = ProductSubscription.create()
                .addOrderbook(CurrencyPair.BTC_USD)
                .addOrderbook(CurrencyPair.ETH_USD)
                .build();

        StreamingExchange coinbaseProExchange = StreamingExchangeFactory.INSTANCE.createExchange(coinbaseProSpec);

        coinbaseProExchange.connect(productSubscription).blockingAwait();
        Disposable subscription1 = coinbaseProExchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD, 10) //Limit Max Depth
                .subscribe(
                        (trade) -> {
                            LOG.info("Trade: {}", trade);
                            ringBuffer.publishEvent((event, sequence, buffer) -> {
                                event.orderBook = trade;
                                event.exchange = COINBASE_PRO;
                                event.currencyPair = CurrencyPair.BTC_USD;
                            });
                        },
                        throwable -> LOG.error("Error in trade subscription", throwable));
        /*
        Disposable subscription2 = coinbaseProExchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.ETH_USD, 10)
                .subscribe(
                        trade -> LOG.info("Trade: {}", trade),
                        throwable -> LOG.error("Error in trade subscription", throwable));
         */

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
        subscription1.dispose();
//        subscription2.dispose();

        coinbaseProExchange.disconnect().blockingAwait();
        bookkeeper.shutdown();
        LOG.info("END");

 */
    }
}
