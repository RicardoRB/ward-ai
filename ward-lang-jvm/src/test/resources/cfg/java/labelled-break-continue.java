class LabelledJumps {
  void run(int n) {
    outer:
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (j == i) {
          continue outer;
        }
        if (j > 5) {
          break outer;
        }
        work(i, j);
      }
    }
    done();
  }
}
