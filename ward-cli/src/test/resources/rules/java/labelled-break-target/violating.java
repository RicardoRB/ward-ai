class Violating {
  void run(boolean a) {
    outer:
    if (a) {
      go();
    }
  }
  void go() {
  }
}
