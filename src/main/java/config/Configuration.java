package config;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Getter
public class Configuration {

    private OscillationArbitragerConfig oscillationArbitragerConfig;
    private CoinbaseProConfig coinbaseProConfig;
    private KrakenConfig krakenConfig;
    private BitfinexConfig bitfinexConfig;
    private GeminiConfig geminiConfig;

    @Builder
    @Getter
    public static class OscillationArbitragerConfig {
        boolean enabled;
        BigDecimal minGain;
    }

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
    }

    @SuperBuilder
    public static class KrakenConfig extends ExchangeConfig {
    }

    @SuperBuilder
    public static class BitfinexConfig extends ExchangeConfig {
    }

    @SuperBuilder
    public static class GeminiConfig extends ExchangeConfig {
    }
}
