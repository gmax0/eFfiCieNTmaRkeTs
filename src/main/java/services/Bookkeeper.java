package services;

import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Bookkeeper implements EventHandler<OrderBookEvent> {
    private static Logger LOG = LoggerFactory.getLogger(Bookkeeper.class);

    //TODO: Ensure the nested map is thread safe
    private final Map<Exchange, Map<CurrencyPair, OrderBook>> orderBooks = new ConcurrentHashMap<>();

    public Bookkeeper() {
    }

    @Override
    public void onEvent(OrderBookEvent event, long sequence, boolean endOfBatch) {
        this.upsertOrderBook(event.exchange, event.currencyPair, event.orderBook);
    }

    public void upsertOrderBook(Exchange exchange, CurrencyPair currencyPair, OrderBook orderBook) {
//        LOG.info("This is my upsert thread");
        orderBooks.computeIfAbsent(exchange, (k) -> {
            return new HashMap<>();
        });
        orderBooks.get(exchange).put(currencyPair, orderBook);
    }

    public OrderBook getOrderBook(Exchange exchange, CurrencyPair currencyPair) {
//        LOG.debug("This is my get thread");
        return orderBooks.get(exchange).get(currencyPair);
    }

    public Map<Exchange, Map<CurrencyPair, OrderBook>> getAllOrderBooks() {
        return orderBooks;
    }
}
