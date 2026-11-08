class Point {
  int x;

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != Point.class) {
      return false;
    }
    return ((Point) o).x == x;
  }
}
