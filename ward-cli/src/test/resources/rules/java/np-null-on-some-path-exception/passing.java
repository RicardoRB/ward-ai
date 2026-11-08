// passes
final class Check {
  void test(String text) {
    try {
      read();
    } catch (RuntimeException e) {
      if (text == null) {
        return;
      }
      text.length();
    }
  }

  void read() {}
}
