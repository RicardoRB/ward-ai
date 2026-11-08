final class Check {
  void test(String text, boolean flag) {
    if (text == null) {
      flag = false;
    }
    if (flag) {
      text.trim();
    } else {
      text.strip();
    }
  }
}
