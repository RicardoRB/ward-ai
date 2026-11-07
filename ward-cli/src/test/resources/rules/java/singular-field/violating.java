final class Scale {
  private int factor;

  int apply(int value) {
    factor = 2;
    return value * factor;
  }
}
