class Sample {
  static int count;
  static final int LIMIT = 1;
  int value;

  Sample() {
    count = 1;
    Sample.count += 2;
    value = 3;
  }
}
