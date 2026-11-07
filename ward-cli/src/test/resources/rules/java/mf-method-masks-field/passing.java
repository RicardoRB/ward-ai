final class Box {
  private int total;

  void add(int v) {
    total += v;
  }

  int get() {
    int copy = total;
    return copy;
  }
}
