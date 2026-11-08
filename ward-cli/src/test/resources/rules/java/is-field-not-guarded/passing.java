import javax.annotation.concurrent.GuardedBy;

class Counter {
  @GuardedBy("this")
  private int count;

  Counter() {
    count = 0;
  }

  public synchronized int read() {
    return count;
  }

  public int locked() {
    synchronized (this) {
      return count;
    }
  }

  private int helper() {
    return count;
  }
}
