final class Check {
  private static int count;

  synchronized void test() {
    count = count + 1;
  }

  void other() {
    synchronized (this) {
      count = 0;
    }
  }
}
