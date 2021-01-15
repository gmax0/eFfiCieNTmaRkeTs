package services;

import domain.constants.Exchange;
import org.apache.commons.lang3.tuple.Pair;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used to aggregate account balances across exchanges
 * and provides utility methods for reporting on session metrics
 */
public class BalanceCaptor {
    private static final Logger LOG = LoggerFactory.getLogger(BalanceCaptor.class);

    private MetadataAggregator metadataAggregator;
    private Map<Exchange, Map<Currency, BigDecimal>> balances = new ConcurrentHashMap<>();

    //Timestamped Aggregate Currency Balances
    private List<Pair<Instant, Map<Currency, BigDecimal>>> snapshots;

    public BalanceCaptor(MetadataAggregator metadataAggregator, Map<Exchange, List<CurrencyPair>> activeExchangesPairMap) {
        LOG.info("Initializing BalanceCaptor");
        this.metadataAggregator = metadataAggregator;

        //$10 in the crypto of your choice to convert this to a lambda.
        //Load the balances map with all currencies that need to be monitored.
        for (Map.Entry<Exchange, List<CurrencyPair>> entry : activeExchangesPairMap.entrySet()) {
            balances.put(entry.getKey(), new ConcurrentHashMap<>());

            Set<Currency> currencies = new HashSet<>();
            for (CurrencyPair currencyPair: entry.getValue()) {
                currencies.add(currencyPair.base);
                currencies.add(currencyPair.counter);
            }
            for (Currency currency : currencies) {
                balances.get(entry.getKey()).put(currency, BigDecimal.ZERO);
            }
        }
    }

    public void refreshBalances() {
        for (Map.Entry<Exchange, Map<Currency, BigDecimal>> entry : balances.entrySet()) {
            for (Map.Entry<Currency, BigDecimal> entry2 : entry.getValue().entrySet()) {
                BigDecimal availableBalance = metadataAggregator.getBalance(entry.getKey(), entry2.getKey()).getAvailable();
                balances.get(entry.getKey()).put(entry2.getKey(), availableBalance);
            }
        }
    }

  public void captureBalances() {
    Map<Currency, BigDecimal> aggregateBalances = new HashMap<>();

    for (Map.Entry<Exchange, Map<Currency, BigDecimal>> entry : balances.entrySet()) {
      entry
          .getValue()
          .forEach(
              (currency, value) ->
                  aggregateBalances.merge(currency, value, (v1, v2) -> v1.add(v2)));
    }
    LOG.info("Aggregate Balances: {}", aggregateBalances);
  }
}
