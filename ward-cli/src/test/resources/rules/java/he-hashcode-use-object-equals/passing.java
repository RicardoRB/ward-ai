
class Pair {
  public boolean equals(Object o) {
    return true;
  }

  public int hashCode() {
    return 1;
  }
}

class Child extends Base {
  public int hashCode() {
    return 2;
  }
}
