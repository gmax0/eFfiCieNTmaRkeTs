package streams;

import buffer.OrderBookBuffer;
import domain.constants.Exchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;

import java.util.ArrayList;
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
      connectStream();
      createSubscriptions();
    } else {
      getLog().warn("{}ExchangeStream is disabled, not attempting WSS connection.", getExchange());
    }
  }

  public void shutdown() {
    if (this.isEnabled && this.streamingExchange.isAlive()) {
      disposeSubscriptions();
      disconnectStream();
    } else {
      getLog().info("{}ExchangeStream connection is not alive.", getExchange());
    }
  }

  public void reset() {
    if (this.isEnabled && this.streamingExchange.isAlive()) {
      getLog().info("Resetting {}ExchangeStream subscriptions...", getExchange());
      disposeSubscriptions();
      disconnectStream();
      connectStream();
      createSubscriptions();
    } else {
      getLog().info("{}ExchangeStream connection is not alive.", getExchange());
    }
  }

  private void disposeSubscriptions() {
    getLog().info("Disposing {}ExchangeStream subscriptions...", getExchange());
    this.subscriptions.stream().forEach(subscription -> subscription.dispose());
  }

  private void disconnectStream() {
    getLog().info("Disconnecting from {}ExchangeStream WSS connection...", getExchange());
    this.streamingExchange.disconnect().blockingAwait();
  }

  private void connectStream() {
    getLog().info("Initiating {}ExchangeStream WSS connection...", getExchange());
    this.streamingExchange.connect(productSubscription).blockingAwait();
  }

  private void createSubscriptions() {
    getLog().info("Creating {}ExchangeStream subscriptions...", getExchange());
    this.subscriptions = new ArrayList<>();
    currencyPairs.stream()
        .forEach(
            currencyPair -> {
              getLog().info("{}", currencyPair);
              subscriptions.add(
                  streamingExchange
                      .getStreamingMarketDataService()
                      .getOrderBook(currencyPair, depth)
                      .subscribe(
                          (orderBook) -> {
                            if (orderBook.getAsks().isEmpty() || orderBook.getBids().isEmpty()) {
                              getLog()
                                  .warn(
                                      "Orderbooks containing empty asks or bids detected for {} : {}",
                                      getExchange(),
                                      currencyPair);
                            } else {
                              orderBookBuffer.insert(orderBook, getExchange(), currencyPair);
                            }
                          },
                          throwable -> getLog().error("Error in trade subscription", throwable)));
              // TODO: Send an empty order book to clear out state in arbitrage layer
            });
  }
}
