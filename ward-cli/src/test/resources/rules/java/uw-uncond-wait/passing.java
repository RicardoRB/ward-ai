final class Check {
  private final Object lock = new Object();
  private boolean ready;

  void test() throws InterruptedException {
    synchronized (lock) {
      while (!ready) {
        lock.wait();
      }
    }
  }

  void guarded() throws InterruptedException {
    synchronized (lock) {
      if (!ready) {
        lock.wait();
      }
    }
  }

  void unlocked() throws InterruptedException {
    lock.wait();
  }
}

final class Helpers {
  private final Object lock = new Object();

  static void wait(Object target, long millis) {}

  void sleep() {
    synchronized (lock) {
      Helpers.wait(lock, 10L);
    }
  }
}
