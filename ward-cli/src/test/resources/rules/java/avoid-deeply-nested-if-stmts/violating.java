final class Nesting {
  void check(boolean a, boolean b, boolean c) {
    if (a) {
      if (b) {
        if (c) {
          run();
        }
      }
    }
  }

  void run() {}
}
