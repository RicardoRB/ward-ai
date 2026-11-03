final class Linear {
  int steps(int n) {
    if (n > 0) {
      n = n + 1;
    }
    for (int i = 0; i < 2; i++) {
      n = n + i;
    }
    return n;
  }
}
