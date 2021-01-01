package buffer.events;

import com.lmax.disruptor.EventTranslatorOneArg;
import domain.Trade;
import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

@NoArgsConstructor
@Setter
@Getter
public class TradeEvent {

    private Exchange exchange;
    private CurrencyPair currencyPair;
    private OrderActionType orderActionType;
    private OrderType orderType;
    private BigDecimal price;
    private BigDecimal amount;

    public static final EventTranslatorOneArg<TradeEvent, Trade> TRANSLATOR
            = new EventTranslatorOneArg<TradeEvent, Trade>() {
        @Override
        public void translateTo(TradeEvent tradeEvent, long l, Trade trade) {
            tradeEvent.setExchange(trade.getExchange());
            tradeEvent.setCurrencyPair(trade.getCurrencyPair());
            tradeEvent.setOrderActionType(trade.getOrderActionType());
            tradeEvent.setOrderType(trade.getOrderType());
            tradeEvent.setPrice(trade.getPrice());
            tradeEvent.setAmount(trade.getAmount());
        }
    };
}
