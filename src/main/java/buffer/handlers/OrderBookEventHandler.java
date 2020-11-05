package buffer.handlers;

import buffer.events.OrderBookEvent;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.Bookkeeper;

public class OrderBookEventHandler implements EventHandler<OrderBookEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(OrderBookEventHandler.class);

    private Bookkeeper bookkeeper;
    //TODO: Inject Journaling service

    public OrderBookEventHandler(Bookkeeper bookkeeper) {
        this.bookkeeper = bookkeeper;
    }

    public void onEvent(OrderBookEvent event, long sequence, boolean endOfBatch) {
        this.bookkeeper.upsertOrderBook(event.exchange, event.currencyPair, event.orderBook);
    }
}
