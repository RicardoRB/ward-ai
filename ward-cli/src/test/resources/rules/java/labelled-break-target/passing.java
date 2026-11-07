class Passing {
  void run(int n) {
    outer:
    for (int i = 0; i < n; i++) {
      go();
    }
    again:
    while (n > 0) {
      break again;
    }
  }
  void go() {
  }
}
