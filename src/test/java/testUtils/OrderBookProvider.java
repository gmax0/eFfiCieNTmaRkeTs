package testUtils;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;

public class OrderBookProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OrderBookProvider.class);

    public static OrderBook getOrderBookFromCSV(CurrencyPair currencyPair, Date date, String bidFileName, String askFileName) {

        ArrayList<LimitOrder> bids = new ArrayList<>();
        ArrayList<LimitOrder> asks = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(OrderBookProvider.class.getClassLoader().getResourceAsStream(bidFileName)))) {
            String line;
            int counter = 0;
            while ((line = br.readLine()) != null) {
                counter++;
                if (counter == 1) {
                    continue;
                }
                String[] values = line.split(",");
                bids.add(new LimitOrder(Order.OrderType.BID, new BigDecimal(values[0]), currencyPair, null, date, new BigDecimal(values[1])));
            }
        } catch (IOException e) {
            LOG.error("Exception caught: ", e);
            return null;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(OrderBookProvider.class.getClassLoader().getResourceAsStream(askFileName)))) {
            String line;
            int counter = 0;
            while ((line = br.readLine()) != null) {
                counter++;
                if (counter == 1) {
                    continue;
                }
                String[] values = line.split(",");
                asks.add(new LimitOrder(Order.OrderType.ASK, new BigDecimal(values[0]), currencyPair, null, date, new BigDecimal(values[1])));
            }
        } catch (IOException e) {
            LOG.error("Exception caught: ", e);
            return null;
        }

        return new OrderBook(date, asks, bids);
    }
}
