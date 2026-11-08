final class Check {
  private final Object lock = new Object();

  void test() throws InterruptedException {
    synchronized (lock) {
      lock.wait();
    }
  }
}
