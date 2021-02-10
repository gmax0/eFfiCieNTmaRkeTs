package config;

import domain.constants.Exchange;
import lombok.Getter;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static domain.constants.Exchange.*;

@Getter
public class ConfigurationManager {
  private static Logger LOG = LoggerFactory.getLogger(ConfigurationManager.class);

  private Configuration config;

  public Map<Exchange, List<CurrencyPair>> getActiveExchangesPairMap() {
    return config.getExchangeConfigs().stream()
        .filter(config -> config.enabled)
        .collect(
            Collectors.toMap(
                Configuration.ExchangeConfig::getExchange,
                Configuration.ExchangeConfig::getCurrencyPairs));
  }

  public ConfigurationManager(InputStream is) throws ConfigurationException {
    YAMLConfiguration yamlConfiguration = new YAMLConfiguration();
    yamlConfiguration.setConversionHandler(new CustomConversionHandler());
    yamlConfiguration.read(is);
    yamlConfiguration
        .getKeys()
        .forEachRemaining(
            k -> {
              LOG.debug(k);
            });
    config =
        Configuration.builder()
            .spatialArbitragerConfig(
                Configuration.SpatialArbitragerConfig.builder()
                    .enabled(yamlConfiguration.getBoolean("strategies.spatial.enabled"))
                    .minGain(yamlConfiguration.getBigDecimal("strategies.spatial.min_gain"))
                    .build())
            .coinbaseProConfig(
                Configuration.CoinbaseProConfig.builder()
                    .exchange(COINBASE_PRO)
                    .enabled(yamlConfiguration.getBoolean("exchange.coinbase_pro.enabled"))
                    .apiKey(
                        yamlConfiguration.getString(
                            "exchange.coinbase_pro.api.credentials.api_key"))
                    .secretKey(
                        yamlConfiguration.getString(
                            "exchange.coinbase_pro.api.credentials.secret_key"))
                    .passphrase(
                        yamlConfiguration.getString(
                            "exchange.coinbase_pro.api.credentials.passphrase"))
                    .refreshRate(yamlConfiguration.getInt("exchange.coinbase_pro.api.refresh_rate"))
                    .currencyPairs(
                        yamlConfiguration.getList(
                            CurrencyPair.class, "exchange.coinbase_pro.websocket.currency_pairs"))
                    .depth(yamlConfiguration.getInt("exchange.coinbase_pro.websocket.depth"))
                    .build())
            .bitfinexConfig(
                Configuration.BitfinexConfig.builder()
                    .exchange(BITFINEX)
                    .enabled(yamlConfiguration.getBoolean("exchange.bitfinex.enabled"))
                    .apiKey(
                        yamlConfiguration.getString("exchange.bitfinex.api.credentials.api_key"))
                    .secretKey(
                        yamlConfiguration.getString("exchange.bitfinex.api.credentials.secret_key"))
                    .refreshRate(yamlConfiguration.getInt("exchange.bitfinex.api.refresh_rate"))
                    .currencyPairs(
                        yamlConfiguration.getList(
                            CurrencyPair.class, "exchange.bitfinex.websocket.currency_pairs"))
                    .depth(yamlConfiguration.getInt("exchange.bitfinex.websocket.depth"))
                    .build())
            .krakenConfig(
                Configuration.KrakenConfig.builder()
                    .exchange(KRAKEN)
                    .enabled(yamlConfiguration.getBoolean("exchange.kraken.enabled"))
                    .apiKey(yamlConfiguration.getString("exchange.kraken.api.credentials.api_key"))
                    .secretKey(
                        yamlConfiguration.getString("exchange.kraken.api.credentials.secret_key"))
                    .refreshRate(yamlConfiguration.getInt("exchange.kraken.api.refresh_rate"))
                    .currencyPairs(
                        yamlConfiguration.getList(
                            CurrencyPair.class, "exchange.kraken.websocket.currency_pairs"))
                    .depth(yamlConfiguration.getInt("exchange.kraken.websocket.depth"))
                    .build())
            .geminiConfig(
                Configuration.GeminiConfig.builder()
                    .exchange(GEMINI)
                    .enabled(yamlConfiguration.getBoolean("exchange.gemini.enabled"))
                    .apiKey(yamlConfiguration.getString("exchange.gemini.api.credentials.api_key"))
                    .secretKey(
                        yamlConfiguration.getString("exchange.gemini.api.credentials.secret_key"))
                    .refreshRate(yamlConfiguration.getInt("exchange.gemini.api.refresh_rate"))
                    .currencyPairs(
                        yamlConfiguration.getList(
                            CurrencyPair.class, "exchange.gemini.websocket.currency_pairs"))
                    .depth(yamlConfiguration.getInt("exchange.gemini.websocket.depth"))
                    .build())
            .binanceConfig(
                Configuration.BinanceConfig.builder()
                    .exchange(BINANCE)
                    .enabled(yamlConfiguration.getBoolean("exchange.binance.enabled"))
                    .apiKey(yamlConfiguration.getString("exchange.binance.api.credentials.api_key"))
                    .secretKey(
                        yamlConfiguration.getString("exchange.binance.api.credentials.secret_key"))
                    .refreshRate(yamlConfiguration.getInt("exchange.binance.api.refresh_rate"))
                    .currencyPairs(
                        yamlConfiguration.getList(
                            CurrencyPair.class, "exchange.binance.websocket.currency_pairs"))
                    .depth(yamlConfiguration.getInt("exchange.binance.websocket.depth"))
                    .build())
            .cexConfig(
                Configuration.CexConfig.builder()
                    .exchange(CEX)
                    .enabled(yamlConfiguration.getBoolean("exchange.cex.enabled"))
                    .apiKey(yamlConfiguration.getString("exchange.cex.api.credentials.api_key"))
                    .user(yamlConfiguration.getString("exchange.cex.api.credentials.user"))
                    .secretKey(
                        yamlConfiguration.getString("exchange.cex.api.credentials.secret_key"))
                    .refreshRate(yamlConfiguration.getInt("exchange.cex.api.refresh_rate"))
                    .currencyPairs(
                        yamlConfiguration.getList(
                            CurrencyPair.class, "exchange.cex.websocket.currency_pairs"))
                    .depth(yamlConfiguration.getInt("exchange.cex.websocket.depth"))
                    .build())
            .build();
  }
}
