package buffer;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import buffer.events.OrderBookEvent;
import com.lmax.disruptor.dsl.ProducerType;
import constants.Exchange;
import lombok.Builder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.Bookkeeper;
import services.OscillationArbitrager;

/**
 * Disruptor-backed Buffer exclusively used for OrderBookEvents
 * TODO: Consolidate message types and buffer to a single generic classes?
 * See https://stackoverflow.com/questions/16845956/how-to-use-a-disruptor-with-multiple-message-types
 */
public class OrderBookBuffer {

    private static Logger LOG = LoggerFactory.getLogger(OrderBookBuffer.class);
    private static final String bufferName = "orderbook-buffer";
    private Disruptor<OrderBookEvent> disruptor;
    private RingBuffer ringBuffer;

    @Builder
    public OrderBookBuffer(Bookkeeper bookkeeper, OscillationArbitrager oscillationArbitrager) {
        //TODO: configurize disruptor parameters
        this.disruptor = new Disruptor(
                OrderBookEvent::new,
                1024,
                new ThreadFactory(this.bufferName),
                ProducerType.MULTI,
                new SleepingWaitStrategy());

        disruptor.handleEventsWith(bookkeeper, oscillationArbitrager);
//        disruptor.handleEventsWith(bookkeeper, bookkeeper);
//        disruptor.after(bookkeeper);
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<>());

        this.ringBuffer = disruptor.getRingBuffer();

    }

    public void insert(OrderBook orderBook, Exchange exchange, CurrencyPair currencyPair) {
        ringBuffer.publishEvent(OrderBookEvent.TRANSLATOR, orderBook, exchange, currencyPair);
    }

    public void start() {
        LOG.info("Starting disruptor.");
        disruptor.start();
    }

    public void shutdown() {
        LOG.info("Shutting down disruptor.");
        disruptor.shutdown();
    }

    private class ExceptionHandler<OrderBookEvent> implements com.lmax.disruptor.ExceptionHandler<OrderBookEvent> {
        @Override
        public void handleEventException(Throwable ex, long sequence, Object event) {
            LOG.error("Exception occurred while processing a {}.",
                    ((OrderBookEvent) event).getClass().getSimpleName(), ex);
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            LOG.error("Failed to start the {} buffer.", bufferName, ex);
            disruptor.shutdown();
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            LOG.error("Error while shutting down the DisruptorCommandBus", ex);
        }
    }
}


