package buffer.events;

import com.lmax.disruptor.EventTranslatorVararg;
import domain.Trade;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class TradeEvent {

    private Trade trade1;
    private Trade trade2;
    private Trade trade3;

    public static final EventTranslatorVararg<TradeEvent> TRANSLATOR
            = new EventTranslatorVararg<TradeEvent>() {
        @Override
        public void translateTo(TradeEvent tradeEvent, long l, Object... trades) {
            tradeEvent.setTrade1((Trade) trades[0]);
            tradeEvent.setTrade2((Trade) trades[1]);

            if (trades.length > 2) {
                tradeEvent.setTrade3((Trade) trades[2]);
            }
        }
    };
}
