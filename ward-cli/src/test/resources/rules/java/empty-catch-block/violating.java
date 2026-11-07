final class EmptyCatchViolations {
  void run() {
    try {
      run();
    } catch (RuntimeException ignored) {
    }
    try {
      run();
    } catch (IllegalStateException e) {
      // expected to happen
    }
  }
}
