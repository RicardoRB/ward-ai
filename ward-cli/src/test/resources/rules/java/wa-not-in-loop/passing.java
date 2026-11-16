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
    Sample.Waits.wait(lock, 10L);
  }

  static final class Waits {
    private Waits() {}

    static void wait(final Object lock, final long millis) {
      if (lock == null || millis < 0) {
        throw new IllegalArgumentException();
      }
    }
  }
}
