class Point {
  private final int x;

  @Override
  public boolean equals(Object other) {
    return other instanceof Point;
  }

  @Override
  public int hashCode() {
    return x;
  }
}

class Pair {
  private final int first;

  @Override
  public boolean equals(final Object other) {
    return other instanceof Pair;
  }

  @Override
  public int hashCode() {
    return first;
  }
}

class Matcher {
  boolean looksLikeEquals(String source) {
    return source.contains("boolean equals(Object other)");
  }
}
