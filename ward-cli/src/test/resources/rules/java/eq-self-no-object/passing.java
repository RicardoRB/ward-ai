
class Plain extends Base {
  public boolean equals(Plain other) {
    return true;
  }

  @Override
  public boolean equals(Object other) {
    return true;
  }
}

class Solo {
  public boolean equals(Solo other) {
    return true;
  }
}

abstract class Shape extends Base {
  abstract boolean equals(Shape other);
}
