final class Check {
  int depth(int n) {
    if (n == 0) {
      return 0;
    }
    return depth(n - 1) + 1;
  }
}
