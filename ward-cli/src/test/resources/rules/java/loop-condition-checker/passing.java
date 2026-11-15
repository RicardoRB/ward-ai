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

  void countDown(int n) {
    int left = n;
    while (--left >= 0) {
      go();
    }
    for (int k = n; --k >= 0;) {
      go();
    }
    int seen = 0;
    while (seen++ < n) {
      go();
    }
  }
}
