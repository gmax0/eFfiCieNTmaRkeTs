package util;

public class ThreadFactory implements java.util.concurrent.ThreadFactory {
  private int counter;
  private String name;

  public ThreadFactory(String name) {
    counter = 1;
    this.name = name;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r, String.format("%s-%d", name, counter++));
    //            t.setPriority(Thread.MAX_PRIORITY); //TODO: possibly will be made configurable
    // later on
    t.setDaemon(true);
    return t;
  }
}
