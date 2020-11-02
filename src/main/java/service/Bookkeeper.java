package service;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import common.dto.Exchange;
import common.dto.OrderBookEvent;
import common.config.BookkeeperConfig;
import lombok.Builder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Bookkeeper {
    private static Logger LOG = LoggerFactory.getLogger(Bookkeeper.class);

    private final Disruptor<OrderBookEvent> disruptor;
    private final RingBuffer<OrderBookEvent> ringBuffer;

    private final Map<Exchange, Map<CurrencyPair, OrderBook>> orderBooks = new HashMap<>();

    @Builder
    public Bookkeeper(BookkeeperConfig cfg) {
        disruptor = new Disruptor(
                OrderBookEvent::new,
                cfg.getBufferSize(),
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new SleepingWaitStrategy()); //TODO: config

        //Disruptor Event Translator
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            orderBooks.computeIfAbsent(event.exchange, (k) -> {
                return new HashMap<>();
            });
            orderBooks.get(event.exchange).put(event.currencyPair, event.orderBook);
        });

        ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
    }

    public void shutdown() {
        disruptor.shutdown();
    }

    public RingBuffer getRingBuffer() {
        return ringBuffer;
    }

    public OrderBook getOrderBook(Exchange exchange, CurrencyPair currencyPair) {
        return orderBooks.get(exchange).get(currencyPair);
    }
}
