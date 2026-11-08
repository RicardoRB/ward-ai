// passes
final class Check {
  void test() {
    String line = null;
    try {
      line = read();
    } catch (RuntimeException e) {
      if (line != null) {
        line.length();
      }
    }
  }

  String read() {
    return "";
  }
}
