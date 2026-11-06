final class Checks {
  void check(int value) {
    if (value <= 0) {
      throw new IllegalArgumentException("value");
    }
  }
}
