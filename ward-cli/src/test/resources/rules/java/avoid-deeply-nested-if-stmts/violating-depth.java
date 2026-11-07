final class Nesting {
  void check(boolean a, boolean b) {
    if (a) {
      if (b) {
        run();
      }
    }
  }

  void run() {}
}
