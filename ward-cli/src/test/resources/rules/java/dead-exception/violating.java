final class Guard {
  void check(int value) {
    if (value < 0) {
      new IllegalArgumentException("negative");
    }
  }
}
