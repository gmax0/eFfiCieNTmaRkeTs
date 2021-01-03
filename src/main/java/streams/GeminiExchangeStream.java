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

    private StreamingExchange streamingExchange;
    private ProductSubscription productSubscription;
    private List<Disposable> subscriptions;

    private OrderBookBuffer orderBookBuffer;
    protected List<CurrencyPair> currencyPairs;
    private int depth;

    public GeminiExchangeStream(Configuration config, OrderBookBuffer orderBookBuffer) {
        if (config.getGeminiConfig().isEnabled()) {
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
            LOG.warn("Gemini is disabled.");
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
                                        orderBookBuffer.insert(trade, GEMINI, currencyPair);
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
