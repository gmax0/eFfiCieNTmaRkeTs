package buffer;

import buffer.events.TradeEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import domain.Trade;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.TradePublisher;
import util.ThreadFactory;

/**
 * Disruptor-backed Buffer exclusively used for TradeEvents TODO: Consolidate message types and
 * buffer to a single generic classes? See
 * https://stackoverflow.com/questions/16845956/how-to-use-a-disruptor-with-multiple-message-types
 */
public class TradeBuffer {
  private static Logger LOG = LoggerFactory.getLogger(OrderBookBuffer.class);
  private static final String bufferName = "tradeBufferConsumer";
  private Disruptor<TradeEvent> disruptor;
  private RingBuffer ringBuffer;

  @Builder
  public TradeBuffer(TradePublisher tradePublisher) {
    // TODO: configurize disruptor parameters
    this.disruptor =
        new Disruptor(
            TradeEvent::new,
            1024,
            new ThreadFactory(this.bufferName),
            ProducerType.MULTI,
            new SleepingWaitStrategy());

    disruptor.handleEventsWith(tradePublisher);
    disruptor.setDefaultExceptionHandler(new TradeBuffer.ExceptionHandler<>());

    this.ringBuffer = disruptor.getRingBuffer();
  }

  /**
   * Publishes a TradeEvent containing two trades to the RingBuffer. Note the arguments' positional
   * requirements.
   *
   * @param trade1 - Trade corresponding to the BUY order at a lower price
   * @param trade2 - Trade corresponding to the SELL order at a higher price
   */
  public void insert(Trade trade1, Trade trade2) {
    ringBuffer.publishEvent(TradeEvent.TRANSLATOR, trade1, trade2);
  }

  public void insert(Trade trade1, Trade trade2, Trade trade3) {
    ringBuffer.publishEvent(TradeEvent.TRANSLATOR, trade1, trade2, trade3);
  }

  public void start() {
    disruptor.start();
    LOG.info("Started TradeBuffer disruptor.");
  }

  public void shutdown() {
    disruptor.shutdown();
    LOG.info("Shut down TradeBuffer disruptor.");
  }

  private class ExceptionHandler<TradeEvent>
      implements com.lmax.disruptor.ExceptionHandler<TradeEvent> {
    @Override
    public void handleEventException(Throwable ex, long sequence, Object event) {
      LOG.error(
          "Exception occurred while processing a {}.",
          ((TradeEvent) event).getClass().getSimpleName(),
          ex);
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
