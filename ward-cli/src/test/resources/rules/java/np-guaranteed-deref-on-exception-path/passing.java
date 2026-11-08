// passes
final class Check {
  void test(String text) {
    try {
      read();
    } catch (RuntimeException e) {
      if (text == null) {
        return;
      }
      if (flag()) {
        text.trim();
      } else {
        text.strip();
      }
    }
  }

  boolean flag() {
    return true;
  }

  void read() {}
}
