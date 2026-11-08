final class Point {
  int x;

  @Override
  public boolean equals(Object o) {
    return o != null && o.getClass() == Point.class && ((Point) o).x == x;
  }
}

class Line {
  int y;

  @Override
  public boolean equals(Object o) {
    return o != null && getClass() == o.getClass() && ((Line) o).y == y;
  }
}
