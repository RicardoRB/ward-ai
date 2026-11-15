class Violating {
  void run(Object a, Object c, boolean b) {
    if (a != c) {
      go();
    } else {
      stop();
    }
    if (!b) {
      go();
    } else {
      stop();
    }
  }
  void go() {
  }
  void stop() {
  }
}
