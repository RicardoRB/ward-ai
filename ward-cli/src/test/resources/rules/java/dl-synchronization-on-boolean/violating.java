final class Sample {
  void run(java.lang.Boolean lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
