package buffer.events;

import com.lmax.disruptor.EventTranslatorThreeArg;
import constants.Exchange;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

@NoArgsConstructor
@Setter
public class OrderBookEvent {
    public OrderBook orderBook;
    public Exchange exchange;
    public CurrencyPair currencyPair;

    public static final EventTranslatorThreeArg<OrderBookEvent, OrderBook, Exchange, CurrencyPair> TRANSLATOR
            = new EventTranslatorThreeArg<OrderBookEvent, OrderBook, Exchange, CurrencyPair>() {
        @Override
        public void translateTo(OrderBookEvent event, long sequence, OrderBook arg0, Exchange arg1, CurrencyPair arg2) {
            event.setOrderBook(arg0);
            event.setExchange(arg1);
            event.setCurrencyPair(arg2);
        }
    };

}
