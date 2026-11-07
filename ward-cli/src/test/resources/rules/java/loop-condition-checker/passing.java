class Passing {
  void run(int n) {
    int i = 0;
    while (i < n) {
      i++;
    }
    for (int j = 0; j < n; j++) {
      go();
    }
    while (i > 0) {
      go();
      break;
    }
    while (this.count < n) {
      go();
    }
  }
  int count;
  void go() {
  }
}
