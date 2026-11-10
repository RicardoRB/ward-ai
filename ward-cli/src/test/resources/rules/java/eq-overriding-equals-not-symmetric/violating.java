class Special extends Base {
  public boolean equals(Object other) {
    return other instanceof Special && super.equals(other);
  }
}
