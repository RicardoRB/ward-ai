final class EmptyCatchPassing {
  void run() {
    try {
      run();
    } catch (RuntimeException e) {
      run();
    }
    try {
      run();
    } catch (IllegalStateException e) {
      // expected to happen
    }
  }
}
