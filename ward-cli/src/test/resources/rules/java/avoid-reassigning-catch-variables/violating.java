final class Parsing {
  void parse() {
    try {
      run();
    } catch (Exception e) {
      e = new IllegalStateException();
    }
  }
}
