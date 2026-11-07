final class Access {
  void check(boolean a, boolean b) {
    if (a) {
      if (b) {
        run();
      }
    }
  }

  void run() {}
}
