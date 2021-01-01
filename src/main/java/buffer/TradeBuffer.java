package buffer;

import buffer.events.OrderBookEvent;
import buffer.events.TradeEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import domain.Trade;
import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import lombok.Builder;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.TradePublisher;
import util.ThreadFactory;

import java.math.BigDecimal;

/**
 * Disruptor-backed Buffer exclusively used for TradeEvents
 * TODO: Consolidate message types and buffer to a single generic classes?
 * See https://stackoverflow.com/questions/16845956/how-to-use-a-disruptor-with-multiple-message-types
 */
public class TradeBuffer {
    private static Logger LOG = LoggerFactory.getLogger(OrderBookBuffer.class);
    private static final String bufferName = "tradeBufferConsumer";
    private Disruptor<TradeEvent> disruptor;
    private RingBuffer ringBuffer;

    @Builder
    public TradeBuffer(TradePublisher tradePublisher) {
        //TODO: configurize disruptor parameters
        this.disruptor = new Disruptor(
                TradeEvent::new,
                1024,
                new ThreadFactory(this.bufferName),
                ProducerType.MULTI,
                new SleepingWaitStrategy());

        disruptor.handleEventsWith(tradePublisher);
//        disruptor.after(bookkeeper);
        disruptor.setDefaultExceptionHandler(new TradeBuffer.ExceptionHandler<>());

        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void insert(Trade trade) {
        ringBuffer.publishEvent(TradeEvent.TRANSLATOR, trade);
    }

    public void start() {
        disruptor.start();
        LOG.info("Started TradeBuffer disruptor.");
    }

    public void shutdown() {
        disruptor.shutdown();
        LOG.info("Shut down TradeBuffer disruptor.");
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
