package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import info.bitrich.xchangestream.coinbasepro.CoinbaseProStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static constants.Exchange.COINBASE_PRO;


public class CoinbaseProExchangeStream {

    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeStream.class);

    private StreamingExchange streamingExchange;
    private ProductSubscription productSubscription;
    private List<Disposable> subscriptions;

    private OrderBookBuffer orderBookBuffer;
    protected List<CurrencyPair> currencyPairs;
    private int depth;

    public CoinbaseProExchangeStream(Configuration config,
                                     OrderBookBuffer orderBookBuffer) {
        if (config.getCoinbaseProConfig().isEnabled()) {
            ExchangeSpecification coinbaseProSpec = new CoinbaseProStreamingExchange().getDefaultExchangeSpecification();

            //Setup ProductSubscription
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
            for (CurrencyPair currencyPair : config.getCoinbaseProConfig().getCurrencyPairs()) {
                builder = builder.addOrderbook(currencyPair);
            }
            this.productSubscription = builder.build();

            this.orderBookBuffer = orderBookBuffer;
            this.currencyPairs = config.getCoinbaseProConfig().getCurrencyPairs();
            this.depth = config.getCoinbaseProConfig().getDepth();
            this.subscriptions = new ArrayList<>();

            this.streamingExchange = StreamingExchangeFactory.INSTANCE.createExchange(coinbaseProSpec);
        } else {
            LOG.info("CoinbasePro is disabled."); //TODO: Add exception here?
        }
    }

    public void start() {
        LOG.info("Initiating exchange connection...");
        this.streamingExchange.connect(productSubscription).blockingAwait();

        LOG.info("Creating subscriptions...");
        currencyPairs.stream().forEach(currencyPair -> {
            subscriptions.add(
            streamingExchange.getStreamingMarketDataService()
                    .getOrderBook(currencyPair, depth)
                    .subscribe(
                            (trade) -> {
//                                LOG.info("Trade: {}", trade);
                                orderBookBuffer.insert(trade, COINBASE_PRO, currencyPair);
                            },
                            throwable -> LOG.error("Error in trade subscription", throwable)));
        });
    }

    public void shutdown() {
        LOG.info("Disposing subscriptions...");
        this.subscriptions.stream().forEach(subscription -> subscription.dispose());
        LOG.info("Disconnecting from exchange...");
        this.streamingExchange.disconnect().blockingAwait();
    }
}
