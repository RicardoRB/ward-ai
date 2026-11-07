class Violating {
  void run(boolean a) {
    if (a) go();
    while (a) go();
    for (int i = 0; i < 3; i++) go();
    if (a) {
      go();
    } else go();
  }
  void go() {
  }
}
