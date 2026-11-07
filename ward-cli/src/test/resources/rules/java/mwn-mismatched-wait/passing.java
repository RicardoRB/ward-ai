final class Sample {
  void run(Object lock) throws Exception {
    synchronized (lock) {
      while (lock.hashCode() == 0) {
        lock.wait();
      }
    }
  }
}
