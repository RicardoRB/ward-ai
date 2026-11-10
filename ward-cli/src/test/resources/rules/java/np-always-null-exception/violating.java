final class Check {
  void test() {
    String line = null;
    try {
      line = read();
    } catch (RuntimeException e) {
      line.length();
    }
  }

  String read() {
    return "";
  }
}
