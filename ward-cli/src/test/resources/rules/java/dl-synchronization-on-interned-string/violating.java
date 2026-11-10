final class Sample {
  void run(java.lang.String lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
