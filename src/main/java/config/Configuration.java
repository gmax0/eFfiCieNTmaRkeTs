package config;

import lombok.Builder;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;

import java.util.List;

@Builder
@Getter
public class Configuration {

    private CoinbaseProConfig coinbaseProConfig;
    private KrakenConfig krakenConfig;

    @Builder
    @Getter
    public static class CoinbaseProConfig {
        boolean enabled;
        String apiKey;
        String secretKey;
        String passphrase;
        List<CurrencyPair> currencyPairs;
        int depth;
        int refreshRate;
    }

    @Builder
    @Getter
    public static class KrakenConfig {

    }
}
