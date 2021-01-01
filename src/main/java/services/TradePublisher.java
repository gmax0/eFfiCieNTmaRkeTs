package services;

import buffer.events.TradeEvent;
import com.lmax.disruptor.EventHandler;
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
        LOG.info("Received trading event: {} {} {} {} {} {}", event.getExchange(), event.getCurrencyPair(), event.getOrderActionType(),
                event.getOrderType(), event.getPrice(), event.getAmount());
    }
}
