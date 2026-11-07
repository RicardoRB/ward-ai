final class Check {
  int depth(int n) {
    return depth(n - 1) + 1;
  }
}
