abstract class ShapeOk {
  public int compareTo(Object other) {
    return 0;
  }

  abstract void draw();
}

abstract class Sized implements Comparable<Sized> {
  public int compareTo(Sized other) {
    return 0;
  }
}

class Concrete {
  public int compareTo(Concrete other) {
    return 0;
  }
}
