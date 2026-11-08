import java.util.concurrent.atomic.AtomicInteger;

class Hits implements Runnable {
  private int hits;
  private final AtomicInteger total = new AtomicInteger();

  public synchronized void run() {
    hits++;
    total.incrementAndGet();
  }

  public synchronized int read() {
    return hits;
  }
}

class Plain {
  private int hits;

  void run() {
    hits++;
  }

  int read() {
    return hits;
  }
}
