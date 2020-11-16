package buffer.events;

import com.lmax.disruptor.EventTranslatorVararg;
import constants.Exchange;
import constants.OrderActionType;
import constants.OrderType;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@NoArgsConstructor
@Setter
public class TradeEvent {

    private Exchange exchange;
    private OrderActionType orderActionType;
    private OrderType orderType;
    private BigDecimal amount;

    public static final EventTranslatorVararg<TradeEvent> TRANSLATOR
            = new EventTranslatorVararg<TradeEvent>() {
        @Override
        public void translateTo(TradeEvent tradeEvent, long l, Object... objects) {
        }
    };
}
