final class Check {
  int depth(int n) {
    return depth(n - 1) + 1;
  }

  int twice(int n) {
    return twice(n);
  }

  int twice(long n) {
    return 2;
  }
}
