class Point {
  public int hashCode() {
    return 0;
  }
}

class Checks {
  void magicNumberSkipsHashCode() {}

  int hashCode() {
    return 1;
  }
}
