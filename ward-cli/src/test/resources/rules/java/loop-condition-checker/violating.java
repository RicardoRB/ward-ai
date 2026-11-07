class Violating {
  void run(int n) {
    int i = 0;
    while (i < n) {
      go();
    }
    for (int j = 0; j < n;) {
      go();
    }
  }
  void go() {
  }
}
