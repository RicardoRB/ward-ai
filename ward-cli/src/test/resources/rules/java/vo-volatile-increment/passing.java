final class Sample {
  java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
  void run() {
    count.incrementAndGet();
  }
}
