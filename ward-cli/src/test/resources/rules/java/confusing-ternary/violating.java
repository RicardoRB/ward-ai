class Violating {
  void run(Object a, boolean b) {
    if (a != null) {
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
