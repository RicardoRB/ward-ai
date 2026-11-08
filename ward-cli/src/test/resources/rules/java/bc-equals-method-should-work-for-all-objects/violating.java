class Key {
  int id;

  @Override
  public boolean equals(Object o) {
    Key other = (Key) o;
    return id == other.id;
  }
}
