package streams;

import buffer.OrderBookBuffer;
import domain.constants.Exchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;

import java.util.List;

public abstract class AbstractExchangeStream {

  abstract Logger getLog();

  abstract Exchange getExchange();

  boolean isEnabled;

  StreamingExchange streamingExchange;
  ProductSubscription productSubscription;
  List<Disposable> subscriptions;

  OrderBookBuffer orderBookBuffer;
  List<CurrencyPair> currencyPairs;
  int depth;

  public void start() {
    if (this.isEnabled) {
      getLog().info("Initiating {}ExchangeStream WSS connection...", getExchange());
      this.streamingExchange.connect(productSubscription).blockingAwait();

      getLog().info("Creating {}ExchangeStream subscriptions...", getExchange());
      currencyPairs.stream()
          .forEach(
              currencyPair -> {
                getLog().info("{}", currencyPair);
                subscriptions.add(
                    streamingExchange
                        .getStreamingMarketDataService()
                        .getOrderBook(currencyPair, depth)
                        .subscribe(
                            (trade) -> {
                              // LOG.info("Trade: {}", trade);
                              orderBookBuffer.insert(trade, getExchange(), currencyPair);
                            },
                            throwable -> getLog().error("Error in trade subscription", throwable)));
                            //TODO: Send an empty order book to clear out state in arbitrage layer
              });
    } else {
      getLog().warn("{}ExchangeStream is disabled, not attempting WSS connection.", getExchange());
    }
  }

  public void shutdown() {
    if (this.isEnabled && this.streamingExchange.isAlive()) {
      getLog().info("Disposing {}ExchangeStream subscriptions...", getExchange());
      this.subscriptions.stream().forEach(subscription -> subscription.dispose());
      getLog().info("Disconnecting from {}ExchangeStream WSS connection...");
      this.streamingExchange.disconnect().blockingAwait();
    } else {
      getLog().info("{}ExchangeStream connection is not alive. ");
    }
  }
}
