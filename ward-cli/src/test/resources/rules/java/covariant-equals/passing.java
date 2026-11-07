final class Covariant {
  @Override
  public boolean equals(final Object other) {
    return other instanceof Covariant;
  }

  public boolean equals(final Covariant other) {
    return equals((Object) other);
  }
}
