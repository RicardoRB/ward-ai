final class Check {
  void test(String text) {
    try {
      read();
    } catch (RuntimeException e) {
      if (text == null) {
        read();
      }
      text.length();
    }
  }

  void read() {}
}
