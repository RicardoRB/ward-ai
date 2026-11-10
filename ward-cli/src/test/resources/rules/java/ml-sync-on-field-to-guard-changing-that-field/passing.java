final class Sample {
  Object lock = new Object();
  Object other = new Object();
  void run() {
    synchronized (other) {
      lock = new Object();
    }
  }
}
