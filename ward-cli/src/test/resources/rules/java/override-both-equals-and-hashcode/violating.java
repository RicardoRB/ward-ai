class Point {
  private final int x;

  @Override
  public boolean equals(Object other) {
    return other instanceof Point;
  }
}
