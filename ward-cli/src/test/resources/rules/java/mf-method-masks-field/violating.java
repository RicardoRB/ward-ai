final class Box {
  private int total;

  int sum(int[] values) {
    int total = 0;
    for (int v : values) {
      total += v;
    }
    return total;
  }

  int get() {
    return total;
  }
}
