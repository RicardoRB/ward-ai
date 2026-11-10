final class Reader {
  void read() {
    try {
      parse();
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage());
    }
  }
}
