final class EmptyBlockViolations {
  void run(boolean a) {
    if (a) {
    }
    while (a) {
      // waiting
    }
  }
}
