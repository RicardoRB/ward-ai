final class Scale {
  private int factor;

  void setup() {
    factor = 2;
  }

  int apply(int value) {
    return value * factor;
  }
}
