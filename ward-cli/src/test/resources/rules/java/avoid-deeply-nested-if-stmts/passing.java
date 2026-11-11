final class Nesting {
  void check(boolean a, boolean b) {
    if (a) {
      if (b) {
        run();
      }
    }
  }

  void chain(int a) {
    if (a == 0) {
      run();
    } else if (a == 1) {
      run();
    } else if (a == 2) {
      run();
    } else if (a == 3) {
      run();
    } else {
      run();
    }
  }

  void run() {}
}
