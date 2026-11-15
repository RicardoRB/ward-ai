final class Sample {
  void run(Object lock) throws Exception {
    synchronized (lock) {
      while (lock.hashCode() == 0) {
        lock.wait();
      }
    }
  }

  void helper(Object lock) throws Exception {
    Waits.wait(lock, 10L);
    util.Waits.wait(lock, 10L);
  }
}
