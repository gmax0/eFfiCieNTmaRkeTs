package services;

import buffer.events.TradeEvent;
import com.lmax.disruptor.EventHandler;
import domain.Trade;
import domain.constants.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rest.ExchangeRestAPI;

import java.util.HashMap;
import java.util.Map;

public class TradePublisher implements EventHandler<TradeEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(TradePublisher.class);

    private final Map<Exchange, ExchangeRestAPI> exchangeRestAPIMap = new HashMap<>();

    public TradePublisher() {}

    public TradePublisher(ExchangeRestAPI... exchangeRestAPI) {
        //Load the Exchange -> ExchangeRestAPI map
        for (ExchangeRestAPI exchangeRestAPI1 : exchangeRestAPI) {
            exchangeRestAPIMap.put(exchangeRestAPI1.getExchangeName(), exchangeRestAPI1);
        }
    }

    @Override
    public void onEvent(TradeEvent event, long sequence, boolean endOfBatch) {
        Trade trade1 = event.getTrade1();
        Trade trade2 = event.getTrade2();
        Trade trade3 = event.getTrade3();

        if (trade1 != null) {
            LOG.info("Trade 1: {} {} {} {} {} {} {}", trade1.getExchange(), trade1.getCurrencyPair(), trade1.getOrderActionType(),
                    trade1.getOrderType(), trade1.getPrice(), trade1.getAmount(), trade1.getTimeDiscovered());
        }
        if (trade2 != null) {
            LOG.info("Trade 2: {} {} {} {} {} {} {}", trade2.getExchange(), trade2.getCurrencyPair(), trade2.getOrderActionType(),
                    trade2.getOrderType(), trade2.getPrice(), trade2.getAmount(), trade2.getTimeDiscovered());
        }
        if (trade3 != null) {
            LOG.info("Trade 3: {} {} {} {} {} {} {}", trade3.getExchange(), trade3.getCurrencyPair(), trade3.getOrderActionType(),
                    trade3.getOrderType(), trade3.getPrice(), trade3.getAmount(), trade3.getTimeDiscovered());
        }
    }
}
