final class Check {
  private final Object first = new Object();
  private final Object second = new Object();

  void test() throws InterruptedException {
    synchronized (first) {
      synchronized (second) {
        second.wait();
      }
    }
  }
}
