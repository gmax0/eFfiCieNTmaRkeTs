package domain;

import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Builder
public class Trade {
    private Exchange exchange;
    private CurrencyPair currencyPair;
    private OrderActionType orderActionType;
    private OrderType orderType;
    private BigDecimal price;
    private BigDecimal amount;

    //For Internal Use
    private Instant timeDiscovered;

    @Override
    public int hashCode() {
        return Objects.hash(exchange, currencyPair, orderActionType, orderType, price, amount);
    }
}
