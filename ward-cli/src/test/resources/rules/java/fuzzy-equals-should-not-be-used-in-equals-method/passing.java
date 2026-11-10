final class T {
  double v;

  @Override
  public boolean equals(Object o) {
    return o instanceof T t && v == t.v;
  }

  boolean close(T t) {
    return DoubleMath.fuzzyEquals(v, t.v, 0.1);
  }
}
