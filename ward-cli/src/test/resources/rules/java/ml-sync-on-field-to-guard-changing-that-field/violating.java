final class Sample {
  Object lock = new Object();
  void run() {
    synchronized (lock) {
      lock = new Object();
    }
  }
}
