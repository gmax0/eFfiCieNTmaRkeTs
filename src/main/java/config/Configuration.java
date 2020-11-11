package config;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.knowm.xchange.currency.CurrencyPair;

import java.util.List;

@Builder
@Getter
public class Configuration {

    private CoinbaseProConfig coinbaseProConfig;
    private KrakenConfig krakenConfig;
    private BitfinexConfig bitfinexConfig;

    @SuperBuilder
    @Getter
    public static abstract class ExchangeConfig {
        boolean enabled;
        String apiKey;
        String secretKey;
        String passphrase;
        List<CurrencyPair> currencyPairs;
        int depth;
        int refreshRate;
    }

    @SuperBuilder
    public static class CoinbaseProConfig extends ExchangeConfig {
        int additionalParam;
    }

    @SuperBuilder
    public static class KrakenConfig extends ExchangeConfig {
        int additionalParam;
    }

    @SuperBuilder
    public static class BitfinexConfig extends ExchangeConfig {
        int additionalParam;
    }
}
