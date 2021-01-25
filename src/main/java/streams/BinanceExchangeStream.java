package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import domain.constants.Exchange;
import info.bitrich.xchangestream.binance.BinanceStreamingExchange;
import info.bitrich.xchangestream.bitfinex.BitfinexStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static domain.constants.Exchange.BINANCE;

public class BinanceExchangeStream extends AbstractExchangeStream {
    private static final Logger LOG = LoggerFactory.getLogger(BitfinexExchangeStream.class);
    private static final Exchange exchangeName = BINANCE;

    @Override
    Logger getLog() {
        return LOG;
    }

    @Override
    Exchange getExchange() {
        return this.exchangeName;
    }

    //TODO: Check knowm/XChange's source code and ensure that depth is enforced...subscribing to multiple pairs
    //does not work...?
    public BinanceExchangeStream(Configuration config, OrderBookBuffer orderBookBuffer) {
        if (config.getBinanceConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeStream.", exchangeName);
            this.isEnabled = true;

            ExchangeSpecification exchangeSpecification =
                    new BinanceStreamingExchange().getDefaultExchangeSpecification();

            // Setup ProductSubscription
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
            for (CurrencyPair currencyPair : config.getBinanceConfig().getCurrencyPairs()) {
                builder = builder.addOrderbook(currencyPair);
            }
            this.productSubscription = builder.build();

            this.orderBookBuffer = orderBookBuffer;
            this.currencyPairs = config.getBinanceConfig().getCurrencyPairs();
            this.depth = config.getBinanceConfig().getDepth();
            this.subscriptions = new ArrayList<>();

            this.streamingExchange =
                    StreamingExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
        } else {
            LOG.warn("{}ExchangeStream is disabled.", exchangeName);
        }
    }
}
