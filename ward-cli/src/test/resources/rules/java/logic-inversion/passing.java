final class Compare {
  boolean differs(int a, int b) {
    return a != b && !(a == b && a > 0);
  }
}
