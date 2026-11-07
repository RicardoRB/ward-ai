final class Guard {
  void check(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("value is required");
    }
  }
}
