final class EqualsOnly {
  @Override
  public boolean equals(final Object other) {
    return other == this;
  }
}

final class HashOnly {
  @Override
  public int hashCode() {
    return 1;
  }
}
