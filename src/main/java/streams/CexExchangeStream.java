package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import domain.constants.Exchange;
import info.bitrich.xchangestream.cexio.CexioStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static domain.constants.Exchange.CEX;

//TODO: Filter out the initial orderbooks that come through with empty bids+asks
public class CexExchangeStream extends AbstractExchangeStream {
    private static final Logger LOG = LoggerFactory.getLogger(CexExchangeStream.class);
    private static final Exchange exchangeName = CEX;

    @Override
    Logger getLog() {
        return LOG;
    }

    @Override
    Exchange getExchange() {
        return this.exchangeName;
    }

    public CexExchangeStream(Configuration config, OrderBookBuffer orderBookBuffer) {
        if (config.getCexConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeStream.", exchangeName);
            this.isEnabled = true;

            ExchangeSpecification cexSpec =
                    new CexioStreamingExchange().getDefaultExchangeSpecification();
            cexSpec.setApiKey(config.getCexConfig().getApiKey());
            cexSpec.setSecretKey(config.getCexConfig().getSecretKey());

            // Setup ProductSubscription
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
            for (CurrencyPair currencyPair : config.getCexConfig().getCurrencyPairs()) {
                builder = builder.addOrderbook(currencyPair);
            }
            this.productSubscription = builder.build();

            this.orderBookBuffer = orderBookBuffer;
            this.currencyPairs = config.getCexConfig().getCurrencyPairs();
            this.depth = config.getCexConfig().getDepth();
            this.subscriptions = new ArrayList<>();

            this.streamingExchange = StreamingExchangeFactory.INSTANCE.createExchange(cexSpec);
        } else {
            LOG.warn("{}ExchangeStream is disabled.", exchangeName);
        }
    }
}
