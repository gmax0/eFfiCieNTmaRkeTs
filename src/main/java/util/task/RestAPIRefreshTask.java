package util.task;

import rest.AbstractExchangeRestAPI;

public class RestAPIRefreshTask implements Runnable {
  private AbstractExchangeRestAPI abstractExchangeRestAPI;

  @Override
  public void run() {
    try {
      if (abstractExchangeRestAPI.isEnabled()) {
        abstractExchangeRestAPI.refreshAccountInfo();
        abstractExchangeRestAPI.refreshProducts();
        abstractExchangeRestAPI.refreshFees();
      }
    } catch (Exception e) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), e);
    }
  }

  public RestAPIRefreshTask(AbstractExchangeRestAPI abstractExchangeRestAPI) {
    this.abstractExchangeRestAPI = abstractExchangeRestAPI;
  }
}
