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

  void other(boolean a, boolean b, boolean c) {
    if (a) {
      run();
    } else {
      if (b) {
        run();
      } else {
        if (c) {
          run();
        }
      }
    }
  }

  void run() {}
}
