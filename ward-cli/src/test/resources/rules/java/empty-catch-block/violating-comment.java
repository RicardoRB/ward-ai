final class EmptyCatchComment {
  void run() {
    try {
      run();
    } catch (RuntimeException e) {
      // todo later
    }
  }
}
