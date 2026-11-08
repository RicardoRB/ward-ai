class Name {
  @Override
  public boolean equals(Object o) {
    return o instanceof Object || o instanceof Comparable || o instanceof Sub;
  }
}
