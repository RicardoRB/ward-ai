// passes
final class Check {
  void test(String text, boolean flag) {
    if (text == null) {
      return;
    }
    if (flag) {
      text.trim();
    } else {
      text.strip();
    }
  }
}
