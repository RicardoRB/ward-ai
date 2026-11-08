class Weird {
  @Override
  public boolean equals(Object o) {
    return o != null && hashCode() == o.hashCode();
  }
}
