package util.task;

import services.BalanceCaptor;

public class BalanceCaptorTask implements Runnable {
  private BalanceCaptor balanceCaptor;

  @Override
  public void run() {
    try {
      balanceCaptor.refreshBalances();
      balanceCaptor.captureBalances();
    } catch (Exception e) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), e);
    }
  }

  public BalanceCaptorTask(BalanceCaptor balanceCaptor) {
    this.balanceCaptor = balanceCaptor;
  }
}
