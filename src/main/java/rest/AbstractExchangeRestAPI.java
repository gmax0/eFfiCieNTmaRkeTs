package rest;

import domain.Trade;
import domain.constants.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import services.MetadataAggregator;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractExchangeRestAPI {
  public abstract Logger getLog();

  public abstract Exchange getExchange();

  boolean isEnabled;

  org.knowm.xchange.Exchange exchangeInstance;
  AccountService accountService;
  TradeService tradeService;
  MarketDataService marketDataService;

  MetadataAggregator metadataAggregator;

  // Cached Info
  Map<CurrencyPair, CurrencyPairMetaData> metadataMap;
  Map<CurrencyPair, Fee> feeMap;
  AccountInfo accountInfo;

  public boolean isEnabled() {
    return this.isEnabled;
  }

  public void refreshProducts() throws IOException {
    getLog().info("Refreshing {} Product Info.", getExchange());

    exchangeInstance.remoteInit();
    metadataMap = exchangeInstance.getExchangeMetaData().getCurrencyPairs();
    metadataAggregator.upsertMetadata(getExchange(), metadataMap);

    getLog().debug(metadataMap.toString());
  }

  public void refreshFees() throws IOException {
    getLog().info("Refreshing {} Fee Info.", getExchange());

    feeMap = accountService.getDynamicTradingFees();
    metadataAggregator.upsertFeeMap(getExchange(), feeMap);

    getLog().debug(feeMap.toString());
  }

  public void refreshAccountInfo() throws IOException {
    getLog().info("Refreshing {} Account Info.", getExchange());

    accountInfo = accountService.getAccountInfo();
    metadataAggregator.upsertAccountInfo(getExchange(), accountInfo);

    getLog().debug(accountInfo.toString());
  }

  public String submitTrade(Trade trade) throws IOException {
    getLog().info("Submitting Trade: {}", trade);
    switch (trade.getOrderType()) {
      case STOP:
        return tradeService.placeStopOrder(trade.toStopOrder());
      case LIMIT:
        return tradeService.placeLimitOrder(trade.toLimitOrder());
      case MARKET:
        return tradeService.placeMarketOrder(trade.toMarketOrder());
    }
    getLog().warn("Trade order type not supported: {}", trade.getOrderType());
    return null;
  }
}
