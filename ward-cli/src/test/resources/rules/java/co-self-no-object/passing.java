class PointOk implements Comparable<PointOk> {
  int x;

  public int compareTo(PointOk other) {
    return x - other.x;
  }
}

class Both {
  public int compareTo(Both other) {
    return 0;
  }

  public int compareTo(Object other) {
    return 0;
  }
}

class Derived extends Both {
  public int compareTo(Derived other) {
    return 0;
  }
}

abstract class Base {
  public int compareTo(Base other) {
    return 0;
  }
}
