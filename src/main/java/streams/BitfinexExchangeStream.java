package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import domain.constants.Exchange;
import info.bitrich.xchangestream.bitfinex.BitfinexStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static domain.constants.Exchange.BITFINEX;

public class BitfinexExchangeStream extends AbstractExchangeStream {
    private static final Logger LOG = LoggerFactory.getLogger(BitfinexExchangeStream.class);
    private static final Exchange exchangeName = BITFINEX;

    @Override
    Logger getLog() {
        return LOG;
    }

    @Override
    Exchange getExchange() {
        return this.exchangeName;
    }

    public BitfinexExchangeStream(Configuration config,
                                  OrderBookBuffer orderBookBuffer) {
        if (config.getBitfinexConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeStream.", exchangeName);
            this.isEnabled = true;

            ExchangeSpecification exchangeSpecification = new BitfinexStreamingExchange()
                    .getDefaultExchangeSpecification();

            //Setup ProductSubscription
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
            for (CurrencyPair currencyPair : config.getBitfinexConfig().getCurrencyPairs()) {
                builder = builder.addOrderbook(currencyPair);
            }
            this.productSubscription = builder.build();

            this.orderBookBuffer = orderBookBuffer;
            this.currencyPairs = config.getBitfinexConfig().getCurrencyPairs();
            this.depth = config.getBitfinexConfig().getDepth();
            this.subscriptions = new ArrayList<>();

            this.streamingExchange = StreamingExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
        } else {
            LOG.warn("{}ExchangeStream is disabled.", exchangeName);
        }
    }
}
