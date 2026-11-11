final class Counter {
  private int count;
  private int step = 1;

  Counter(int start) {
    count = start;
  }

  int value() {
    return count + step;
  }
}
