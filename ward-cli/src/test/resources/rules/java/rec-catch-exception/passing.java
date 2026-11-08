final class Passing {
  void run(java.io.Reader r) {
    try {
      r.read();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
