final class Sample {
  void run(java.lang.Long lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
