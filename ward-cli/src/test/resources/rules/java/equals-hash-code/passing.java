final class Pair {
  @Override
  public boolean equals(final Object other) {
    return other instanceof Pair;
  }

  @Override
  public int hashCode() {
    return 1;
  }
}
