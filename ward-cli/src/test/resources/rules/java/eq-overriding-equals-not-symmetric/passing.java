class Special extends Base {
  public boolean equals(Object other) {
    return other != null && getClass() == other.getClass() && super.equals(other);
  }
}
