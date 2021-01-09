package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import domain.constants.Exchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.kraken.KrakenStreamingExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static domain.constants.Exchange.GEMINI;

public class KrakenExchangeStream extends AbstractExchangeStream {
    private static final Logger LOG = LoggerFactory.getLogger(GeminiExchangeStream.class);
    private static final Exchange exchangeName = GEMINI;

    @Override
    Logger getLog() {
        return LOG;
    }

    @Override
    Exchange getExchange() {
        return this.exchangeName;
    }

    public KrakenExchangeStream(Configuration config,
                                     OrderBookBuffer orderBookBuffer) {
        if (config.getKrakenConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeStream.", exchangeName);
            this.isEnabled = true;

            ExchangeSpecification krakenSpec = new KrakenStreamingExchange().getDefaultExchangeSpecification();

            //Setup ProductSubscription
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
            for (CurrencyPair currencyPair : config.getKrakenConfig().getCurrencyPairs()) {
                builder = builder.addOrderbook(currencyPair);
            }
            this.productSubscription = builder.build();

            this.orderBookBuffer = orderBookBuffer;
            this.currencyPairs = config.getKrakenConfig().getCurrencyPairs();
            this.depth = config.getKrakenConfig().getDepth();
            this.subscriptions = new ArrayList<>();

            this.streamingExchange = StreamingExchangeFactory.INSTANCE.createExchange(krakenSpec);
        } else {
            LOG.warn("{}ExchangeStream is disabled.", exchangeName);
        }
    }
}
