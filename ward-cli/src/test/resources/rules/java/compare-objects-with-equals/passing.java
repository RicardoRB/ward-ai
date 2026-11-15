class Sample {
  boolean same(String left, String right) {
    return left.equals(right);
  }

  boolean kind(Color a, Color b) {
    return a == b;
  }

  @Override
  public boolean equals(final Object other) {
    return this == other;
  }
}
