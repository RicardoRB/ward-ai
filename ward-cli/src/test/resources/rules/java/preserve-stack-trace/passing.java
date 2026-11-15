final class Reader {
  void read() {
    try {
      parse();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  void rethrow() {
    try {
      parse();
    } catch (IllegalStateException e) {
      throw e;
    }
  }
}
