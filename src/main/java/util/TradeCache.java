package util;

import domain.Trade;
import domain.constants.Exchange;
import org.knowm.xchange.dto.marketdata.OrderBook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

public class TradeCache {
    private static final Comparator<Trade> ascendingByTime = (t1, t2) -> {
      if (t1.hashCode() == t2.hashCode()) return 0;
      return t1.getTimeDiscovered().compareTo(t2.getTimeDiscovered());
    };

    private TreeSet<Trade> tradeCache = new TreeSet<>(ascendingByTime); //See Trade.hashCode()
    private BigDecimal cacheTime;

    public TradeCache(BigDecimal cacheTime) {
        this.cacheTime = cacheTime;
    }

    public TreeSet<Trade> getCachedTrades() {
        return tradeCache;
    }

    public void insertTrade(Trade trade) {
        tradeCache.add(trade);
    }

    public boolean containsTrade(Trade trade) {
        clearExpiredTrades();
        return tradeCache.contains(trade);
    }

    public void clearExpiredTrades() {
        Iterator<Trade> iterator = tradeCache.iterator();

        while (iterator.hasNext()) {
            if ((Instant.now().getEpochSecond() - iterator.next().getTimeDiscovered().getEpochSecond()) > cacheTime.longValue()) {
                iterator.remove();
            } else {
                return;
            }
        }

    }
}
