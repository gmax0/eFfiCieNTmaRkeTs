package services;

import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataAggregator {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataAggregator.class);

    final private Map<Exchange, Map<CurrencyPair, Fee>> aggregatedFee = new ConcurrentHashMap<>();
    final private Map<Exchange, Map<CurrencyPair, CurrencyPairMetaData>> aggregatedMetadata = new ConcurrentHashMap<>();
    final private Map<Exchange, AccountInfo> aggregatedAccountInfo = new ConcurrentHashMap<>();

    public MetadataAggregator() {
        LOG.info("Instantiated MetadataAggregator.");
    }

    public void upsertFeeMap(Exchange exchange, Map<CurrencyPair, Fee> feeMap) {
        aggregatedFee.put(exchange, feeMap);
    }

    public BigDecimal getMakerFee(Exchange exchange, CurrencyPair currencyPair) {
        if (aggregatedFee.containsKey(exchange) && aggregatedFee.get(exchange).containsKey(currencyPair)) {
            return aggregatedFee.get(exchange).get(currencyPair).getMakerFee();
        } else {
            LOG.warn("Unable to locate maker fee for {} {}", exchange, currencyPair);
            return null;
        }
    }

    public BigDecimal getTakerFee(Exchange exchange, CurrencyPair currencyPair) {
        if (aggregatedFee.containsKey(exchange) && aggregatedFee.get(exchange).containsKey(currencyPair)) {
            return aggregatedFee.get(exchange).get(currencyPair).getTakerFee();
        } else {
            LOG.warn("Unable to locate taker fee for {} {}", exchange, currencyPair);
            return null;
        }
    }

    public void upsertMetadata(Exchange exchange, Map<CurrencyPair, CurrencyPairMetaData> metadataMap) {
        aggregatedMetadata.put(exchange, metadataMap);
    }

    public void upsertAccountInfo(Exchange exchange, AccountInfo accountInfo) {
        aggregatedAccountInfo.put(exchange, accountInfo);
    }

    public Map<CurrencyPair, CurrencyPairMetaData> getCurrencyPairMetaDataMap(Exchange exchange) {
        return aggregatedMetadata.get(exchange);
    }
}
