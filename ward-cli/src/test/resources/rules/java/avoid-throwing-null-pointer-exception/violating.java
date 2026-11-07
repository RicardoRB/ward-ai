final class Guard {
  void check(Object value) {
    if (value == null) {
      throw new NullPointerException("value is required");
    }
  }
}
