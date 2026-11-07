final class Sample {
  void run(java.util.concurrent.ConcurrentHashMap lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
