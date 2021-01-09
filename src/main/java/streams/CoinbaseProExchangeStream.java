package streams;

import buffer.OrderBookBuffer;
import config.Configuration;
import domain.constants.Exchange;
import info.bitrich.xchangestream.coinbasepro.CoinbaseProStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static domain.constants.Exchange.COINBASE_PRO;


public class CoinbaseProExchangeStream extends AbstractExchangeStream {
    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProExchangeStream.class);
    private static final Exchange exchangeName = COINBASE_PRO;

    @Override
    Logger getLog() {
        return LOG;
    }

    @Override
    Exchange getExchange() {
        return this.exchangeName;
    }

    public CoinbaseProExchangeStream(Configuration config,
                                     OrderBookBuffer orderBookBuffer) {
        if (config.getCoinbaseProConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeStream.", exchangeName);
            this.isEnabled = true;

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
            LOG.warn("{}ExchangeStream is disabled.", exchangeName);
        }
    }
}
