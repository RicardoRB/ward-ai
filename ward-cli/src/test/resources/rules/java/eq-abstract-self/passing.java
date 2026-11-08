abstract class Shape {
  @Override
  public boolean equals(Object other) {
    return other == this;
  }

  boolean equals(Shape other, int tolerance) {
    return true;
  }
}

class Square {
  boolean equals(Square other) {
    return true;
  }
}
