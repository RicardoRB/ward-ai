final class Returns {
  int pick(final int value) {
    if (value > 0) {
      return 1;
    }
    return 0;
  }

  void stop(final int value) {
    if (value > 0) {
      return;
    }
  }
}
