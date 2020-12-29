package buffer;

import buffer.events.OrderBookEvent;
import buffer.events.TradeEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.Bookkeeper;
import services.OscillationArbitrager;

/**
 * Disruptor-backed Buffer exclusively used for TradeEvents
 * TODO: Consolidate message types and buffer to a single generic classes?
 * See https://stackoverflow.com/questions/16845956/how-to-use-a-disruptor-with-multiple-message-types
 */
public class TradeBuffer {
    private static Logger LOG = LoggerFactory.getLogger(OrderBookBuffer.class);
    private static final String bufferName = "trade-buffer";
    private Disruptor<OrderBookEvent> disruptor;
    private RingBuffer ringBuffer;

    @Builder
    public TradeBuffer() {
        //TODO: configurize disruptor parameters
        this.disruptor = new Disruptor(
                TradeEvent::new,
                1024,
                new ThreadFactory(this.bufferName),
                ProducerType.MULTI,
                new SleepingWaitStrategy());

//        disruptor.handleEventsWith(bookkeeper, oscillationArbitrager);
//        disruptor.handleEventsWith(bookkeeper, bookkeeper);
//        disruptor.after(bookkeeper);
        disruptor.setDefaultExceptionHandler(new TradeBuffer.ExceptionHandler<>());

        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        LOG.info("Starting disruptor.");
        disruptor.start();
    }

    public void shutdown() {
        LOG.info("Shutting down disruptor.");
        disruptor.shutdown();
    }

    private class ExceptionHandler<TradeEvent> implements com.lmax.disruptor.ExceptionHandler<TradeEvent> {
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
