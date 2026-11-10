class Sample {
  static int count;
  int value;

  Sample() {
    value = 3;
    Other.count = 4;
  }

  static void reset() {
    count = 0;
  }
}
