final class EmptyCatchExempt {
  void run() {
    try {
      run();
    } catch (RuntimeException ignored) {
    }
  }
}
