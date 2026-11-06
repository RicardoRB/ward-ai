final class Parsing {
  void parse() {
    try {
      run();
    } catch (Exception e) {
      IllegalStateException failure = new IllegalStateException(e);
    }
  }
}
