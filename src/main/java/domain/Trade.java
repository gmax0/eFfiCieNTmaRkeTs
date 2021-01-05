package domain;

import domain.constants.Exchange;
import domain.constants.OrderActionType;
import domain.constants.OrderType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Builder
@ToString
public class Trade {
    private Exchange exchange;
    private CurrencyPair currencyPair;
    private OrderType orderType;  //Limit, Stop, or Order
    private Order.OrderType orderActionType; //BID, ASK, EXIT_BID, EXIT_ASK
    private BigDecimal price;
    private BigDecimal amount;

    //For Internal Use
    private Instant timeDiscovered;
    private BigDecimal fee;
    private Boolean feeCurrencyFlag; //Fee to be deducted in quote/counter or base currency. T = base, false = quote/counter

    public StopOrder toStopOrder() {
        return new StopOrder(
                this.getOrderActionType(),
                this.getAmount(),
                this.getCurrencyPair(),
                null,
                null,
                this.getPrice()
        );
    }
    public LimitOrder toLimitOrder() {
        return new LimitOrder(
                this.getOrderActionType(),
                this.getAmount(),
                this.getCurrencyPair(),
                null,
                null,
                this.getPrice()
        );
    }
    public MarketOrder toMarketOrder() {
        return new MarketOrder(
                this.getOrderActionType(),
                this.getAmount(),
                this.getCurrencyPair(),
                null,
                null
        );
    }
    @Override
    public int hashCode() {
        return Objects.hash(exchange, currencyPair, orderActionType, orderType, price, amount);
    }
}
