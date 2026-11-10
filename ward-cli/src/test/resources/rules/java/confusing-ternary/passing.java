class Passing {
  void run(Object a, boolean b) {
    if (a == null) {
      go();
    } else {
      stop();
    }
    if (a != null) {
      go();
    }
    if (!b) {
      go();
    } else if (a == null) {
      stop();
    }
  }
  void go() {
  }
  void stop() {
  }
}
