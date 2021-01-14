package rest;

import domain.Trade;
import domain.constants.Exchange;

import java.io.IOException;

// TODO: consider refactoring this into an abstract class
public interface ExchangeRestAPI {
  Exchange getExchangeName();

  boolean isEnabled();

  void refreshProducts() throws IOException;

  void refreshFees() throws IOException;

  void refreshAccountInfo() throws IOException;

  String submitTrade(Trade trade) throws IOException;
}
