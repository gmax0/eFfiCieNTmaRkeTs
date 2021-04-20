package rest;

import config.Configuration;
import domain.constants.Exchange;
import domain.constants.OrderType;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.cexio.CexIOExchange;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;

import static domain.constants.Exchange.CEX;

public class CexExchangeRestAPI extends AbstractExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(BitfinexExchangeRestAPI.class);
    private final Exchange exchange = CEX;

    @Override
    Logger getLog() {
        return LOG;
    }

    @Override
    public Exchange getExchange() {
        return this.exchange;
    }

    public CexExchangeRestAPI(Configuration cfg, MetadataAggregator metadataAggregator)
            throws IOException {
        if (cfg.getCexConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeRestAPI.", exchange);
            this.isEnabled = true;

            ExchangeSpecification exSpec = new CexIOExchange().getDefaultExchangeSpecification();

            exSpec.setSecretKey(cfg.getCexConfig().getSecretKey());
            exSpec.setApiKey(cfg.getCexConfig().getApiKey());
            exSpec.setUserName(cfg.getCexConfig().getUser());

            exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = exchangeInstance.getAccountService();
            tradeService = exchangeInstance.getTradeService();
            marketDataService = exchangeInstance.getMarketDataService();

            this.metadataAggregator = metadataAggregator;

            // Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.warn("{}ExchangeRestAPI is diabled", exchange);
        }
    }

    // TODO: Add advance limit order params
    LimitOrder customizeLimitOrder(LimitOrder limitOrder, OrderType orderType) {
        return limitOrder;
    }
}
