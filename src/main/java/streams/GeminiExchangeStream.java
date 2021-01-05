package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import info.bitrich.xchangestream.gemini.GeminiStreamingExchange;
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

import static domain.constants.Exchange.GEMINI;

public class GeminiExchangeStream {

    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeStream.class);

    private final domain.constants.Exchange exchangeName = GEMINI;
    private boolean isEnabled = false;

    private StreamingExchange streamingExchange;
    private ProductSubscription productSubscription;
    private List<Disposable> subscriptions;

    private OrderBookBuffer orderBookBuffer;
    protected List<CurrencyPair> currencyPairs;
    private int depth;

    public GeminiExchangeStream(Configuration config, OrderBookBuffer orderBookBuffer) {
        if (config.getGeminiConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeStream.", exchangeName);
            this.isEnabled = true;

            ExchangeSpecification geminiSpec = new GeminiStreamingExchange().getDefaultExchangeSpecification();

            //Setup ProductSubscription
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
            for (CurrencyPair currencyPair : config.getGeminiConfig().getCurrencyPairs()){
                builder = builder.addOrderbook(currencyPair);
            }
            this.productSubscription = builder.build();

            this.orderBookBuffer = orderBookBuffer;
            this.currencyPairs = config.getGeminiConfig().getCurrencyPairs();
            this.depth = config.getCoinbaseProConfig().getDepth();
            this.subscriptions = new ArrayList<>();

            this.streamingExchange = StreamingExchangeFactory.INSTANCE.createExchange(geminiSpec);
        } else {
            LOG.warn("{}ExchangeStream is disabled.", exchangeName);
        }
    }

    public void start() {
        LOG.info("Initiating {}ExchangeStream WSS connection...", exchangeName);
        this.streamingExchange.connect(productSubscription).blockingAwait();

        LOG.info("Creating {}ExchangeStream subscriptions...", exchangeName);
        currencyPairs.stream().forEach(currencyPair -> {
            LOG.info("{}", currencyPair);
            subscriptions.add(
                    streamingExchange.getStreamingMarketDataService()
                            .getOrderBook(currencyPair, depth)
                            .subscribe(
                                    (trade) -> {
                                        orderBookBuffer.insert(trade, GEMINI, currencyPair);
                                    },
                                    throwable -> LOG.error("Error in trade subscription", throwable)));
        });
    }

    public void shutdown() {
        if (this.streamingExchange.isAlive()) {
            LOG.info("Disposing {}ExchangeStream subscriptions...", exchangeName);
            this.subscriptions.stream().forEach(subscription -> subscription.dispose());
            LOG.info("Disconnecting from {}ExchangeStream WSS connection...");
            this.streamingExchange.disconnect().blockingAwait();
        } else {
            LOG.info("{}ExchangeStream connection is not alive. ");
        }
    }
}
