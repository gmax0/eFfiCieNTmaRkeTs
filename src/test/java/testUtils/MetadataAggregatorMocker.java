package testUtils;

import domain.constants.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import services.MetadataAggregator;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetadataAggregatorMocker {

    public static void setMockFee(MetadataAggregator mockMetadataAggregator, Exchange exchange, CurrencyPair currencyPair, BigDecimal makerFee, BigDecimal takerFee) {
        when(mockMetadataAggregator.getFees(exchange, currencyPair)).thenReturn(new Fee(makerFee, takerFee));
    }

    public static void setOrderMinimumVolume(MetadataAggregator mockMetadataAggregator, Exchange exchange, CurrencyPair currencyPair, BigDecimal minVolume) {
        when(mockMetadataAggregator.getMinimumOrderAmount(exchange, currencyPair)).thenReturn(minVolume);
    }

    public static void setMockPriceScale(MetadataAggregator mockMetadataAggregator, Exchange exchange, CurrencyPair currencyPair, Integer priceScale) {
        when(mockMetadataAggregator.getPriceScale(exchange, currencyPair)).thenReturn(priceScale);
    }

    public static void setMockBalance(MetadataAggregator mockMetadataAggregator, Exchange exchange, Currency currency, BigDecimal available) {
        when(mockMetadataAggregator.getBalance(exchange, currency)).thenReturn(new Balance(currency, available, available));
    }
}
