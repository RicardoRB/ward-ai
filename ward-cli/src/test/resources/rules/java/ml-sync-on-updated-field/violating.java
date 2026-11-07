final class Sample {
  Object lock = new Object();
  void reset() {
    lock = new Object();
  }
  void run() {
    synchronized (lock) {
    }
  }
}
