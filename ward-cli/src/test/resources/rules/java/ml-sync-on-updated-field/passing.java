final class Sample {
  final Object lock = new Object();
  void run() {
    synchronized (lock) {
    }
  }
}
