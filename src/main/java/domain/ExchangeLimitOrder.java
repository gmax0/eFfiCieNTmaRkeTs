package domain;

import domain.constants.Exchange;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.knowm.xchange.dto.trade.LimitOrder;

@Getter
@Setter
@Builder
public class ExchangeLimitOrder {
    private Exchange exchange;
    private LimitOrder limitOrder;
}
