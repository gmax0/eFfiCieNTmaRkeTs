package rest;

import domain.constants.Exchange;

import java.io.IOException;

public interface ExchangeRestAPI {
    Exchange getExchangeName();
    void refreshProducts() throws IOException;
    void refreshFees() throws IOException;
    void refreshAccountInfo() throws IOException;
}
