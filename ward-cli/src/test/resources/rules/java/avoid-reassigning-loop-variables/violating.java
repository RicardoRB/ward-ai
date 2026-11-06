final class Loops {
  void run(int[] values) {
    for (int i = 0; i < values.length; i++) {
      i = values[i] + 1;
    }
    for (int value : values) {
      value = value + 1;
    }
  }
}
