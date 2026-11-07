final class ByName {
  public boolean equals(Object other) {
    return other != null && getClass().getName().equals(other.getClass().getName());
  }
}
