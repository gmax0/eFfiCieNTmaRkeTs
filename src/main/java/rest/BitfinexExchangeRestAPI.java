package rest;

import config.Configuration;
import domain.Trade;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.service.BitfinexAccountService;
import org.knowm.xchange.bitfinex.service.BitfinexMarketDataService;
import org.knowm.xchange.bitfinex.service.BitfinexTradeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.Map;

import static domain.constants.Exchange.BITFINEX;

public class BitfinexExchangeRestAPI implements ExchangeRestAPI {
    private static final Logger LOG = LoggerFactory.getLogger(BitfinexExchangeRestAPI.class);

    private final domain.constants.Exchange exchangeName = BITFINEX;
    private Exchange exchangeInstance;
    private BitfinexAccountService accountService;
    private BitfinexTradeService tradeService;
    private BitfinexMarketDataService marketDataService;

    private MetadataAggregator metadataAggregator;

    //Cached Info
    Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
    Map<CurrencyPair, Fee> feeMap;
    AccountInfo accountInfo;

    public BitfinexExchangeRestAPI(Configuration cfg,
                                   MetadataAggregator metadataAggregator) throws IOException {
        if (cfg.getBitfinexConfig().isEnabled()) {
            LOG.info("Initializing {}ExchangeRestAPI.", exchangeName);

            ExchangeSpecification exSpec = new BitfinexExchange().getDefaultExchangeSpecification();

            exSpec.setSecretKey(cfg.getBitfinexConfig().getSecretKey());
            exSpec.setApiKey(cfg.getBitfinexConfig().getApiKey());

            exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
            accountService = (BitfinexAccountService)exchangeInstance.getAccountService();
            tradeService = (BitfinexTradeService)exchangeInstance.getTradeService();
            marketDataService = (BitfinexMarketDataService)exchangeInstance.getMarketDataService();

            this.metadataAggregator = metadataAggregator;

            //Get status details
            /*
            for (Bitfinex product : marketDataService.getStatus()) {
                LOG.info(product.toString());
            }

             */

            //Cache initial calls
            refreshProducts();
            refreshFees();
            refreshAccountInfo();
        } else {
            LOG.info("{}ExchangeRestAPI is diabled", exchangeName);
        }
    }

    @Override
    public domain.constants.Exchange getExchangeName() {
        return exchangeName;
    }

    @Override
    public void refreshProducts() throws IOException {
        LOG.info("Refreshing {} Product Info.", exchangeName);

        exchangeInstance.remoteInit();
        metadataMap = exchangeInstance.getExchangeMetaData().getCurrencyPairs(); //NOTE: trading fees are not correct
        metadataAggregator.upsertMetadata(BITFINEX, metadataMap);

        LOG.debug(metadataMap.toString());
    }

    @Override
    public void refreshFees() throws IOException {
        LOG.info("Refreshing {} Fee Info.", exchangeName);

        feeMap = accountService.getDynamicTradingFees();
        metadataAggregator.upsertFeeMap(BITFINEX, feeMap);

        LOG.debug(feeMap.toString());
    }

    @Override
    public void refreshAccountInfo() throws IOException {
        LOG.info("Refreshing {} Account Info.", exchangeName);

        accountInfo = accountService.getAccountInfo();
        metadataAggregator.upsertAccountInfo(BITFINEX, accountInfo);

        LOG.debug(accountInfo.toString());
    }

    @Override
    public String submitTrade(Trade trade) throws IOException {
        LOG.info("Submitting Trade: {}", trade);
        switch (trade.getOrderType()) {
            case STOP:
                return tradeService.placeStopOrder(trade.toStopOrder());
            case LIMIT:
                return tradeService.placeLimitOrder(trade.toLimitOrder());
            case MARKET:
                return tradeService.placeMarketOrder(trade.toMarketOrder());
        }
        LOG.warn("Trade order type not supported: {}", trade.getOrderType());
        return null;
    }
}
