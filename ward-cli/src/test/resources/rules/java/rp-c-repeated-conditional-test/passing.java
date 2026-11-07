final class Check {
  boolean same(int a, int b) {
    return a == b || a == 1;
  }

  void branch(int a) {
    if (a == 1) {
      a = 2;
    } else if (a == 2) {
      a = 3;
    }
  }
}
