package services;

import domain.constants.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataAggregator {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataAggregator.class);

  private final Map<Exchange, Map<CurrencyPair, Fee>> aggregatedFee = new ConcurrentHashMap<>();
  private final Map<Exchange, Map<CurrencyPair, CurrencyPairMetaData>> aggregatedMetadata =
      new ConcurrentHashMap<>();
  private final Map<Exchange, AccountInfo> aggregatedAccountInfo = new ConcurrentHashMap<>();

  public MetadataAggregator() {
    LOG.info("Instantiated MetadataAggregator.");
  }

  public void upsertFeeMap(Exchange exchange, Map<CurrencyPair, Fee> feeMap) {
    aggregatedFee.put(exchange, feeMap);
  }

  public Fee getFees(Exchange exchange, CurrencyPair currencyPair) {
    if (aggregatedFee.containsKey(exchange)
        && aggregatedFee.get(exchange).containsKey(currencyPair)) {
      return aggregatedFee.get(exchange).get(currencyPair);
    } else {
      LOG.warn("Unable to fees for {} {}", exchange, currencyPair);
      return null;
    }
  }

  public void upsertMetadata(
      Exchange exchange, Map<CurrencyPair, CurrencyPairMetaData> metadataMap) {
    aggregatedMetadata.put(exchange, metadataMap);
  }

  public void upsertAccountInfo(Exchange exchange, AccountInfo accountInfo) {
    aggregatedAccountInfo.put(exchange, accountInfo);
  }

  public Balance getBalance(Exchange exchange, Currency currency) {
    if (aggregatedAccountInfo.containsKey(exchange)) {
      if (exchange.equals(Exchange.KRAKEN)) {
        return aggregatedAccountInfo.get(exchange).getWallets().get(null).getBalance(currency);
      }
      return aggregatedAccountInfo.get(exchange).getWallet().getBalance(currency);
    } else {
      LOG.warn("Unable to located wallet for exchange: {}", exchange);
      return null;
    }
  }

  public BigDecimal getMinimumOrderAmount(Exchange exchange, CurrencyPair currencyPair) {
    if (aggregatedMetadata.containsKey(exchange)
        && aggregatedMetadata.get(exchange).containsKey(currencyPair)) {
      return aggregatedMetadata.get(exchange).get(currencyPair).getMinimumAmount();
    } else {
      LOG.warn(
          "Unable to locate minimumOrderAmount for exchange: {}, currencyPair: {}",
          exchange,
          currencyPair);
      return null;
    }
  }

  public Integer getPriceScale(Exchange exchange, CurrencyPair currencyPair) {
    if (aggregatedMetadata.containsKey(exchange)
        && aggregatedMetadata.get(exchange).containsKey(currencyPair)) {
      return aggregatedMetadata.get(exchange).get(currencyPair).getPriceScale();
    } else {
      LOG.warn(
          "Unable to locate priceScale for exchange: {}, currencyPair: {}", exchange, currencyPair);
      return null;
    }
  }

  public Map<CurrencyPair, CurrencyPairMetaData> getCurrencyPairMetaDataMap(Exchange exchange) {
    return aggregatedMetadata.get(exchange);
  }
}
