final class T {
  void run() {
    try {
      fail();
    } catch (AssertionError e) {
    }
    try {
      fail();
      call();
    } catch (Throwable t) {
    }
  }
}
