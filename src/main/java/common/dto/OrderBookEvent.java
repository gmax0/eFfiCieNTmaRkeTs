package common.dto;

import common.dto.Exchange;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

@NoArgsConstructor
public class OrderBookEvent  {
    public OrderBook orderBook;
    public Exchange exchange;
    public CurrencyPair currencyPair;
}
