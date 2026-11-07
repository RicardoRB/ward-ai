record Point(int x, int y) {
  @Override public int x() { return x; }
  @Override public int y() { return y; }
  public int sum() { return x + y; }
}
