final class Check {
  private static final Object LOCK = new Object();
  private static int count;

  static synchronized void test() {
    count = count + 1;
  }

  void other() {
    synchronized (LOCK) {
      count = 0;
    }
  }

  synchronized void shadow(int count) {
    count = 3;
  }

  void unlocked() {
    count = 1;
  }
}
