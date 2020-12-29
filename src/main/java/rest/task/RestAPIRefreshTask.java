package rest.task;

import rest.ExchangeRestAPI;

public class RestAPIRefreshTask implements Runnable {
    private ExchangeRestAPI exchangeRestAPI;

    @Override
    public void run() {
        try {
            exchangeRestAPI.refreshAccountInfo();
            exchangeRestAPI.refreshProducts();
            exchangeRestAPI.refreshFees();
        } catch (Exception e) {
            Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
        }
    }

    public RestAPIRefreshTask(ExchangeRestAPI exchangeRestAPI) {
        this.exchangeRestAPI = exchangeRestAPI;
    }
}
