final class Guard {
  void check(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("negative");
    }
  }
}
