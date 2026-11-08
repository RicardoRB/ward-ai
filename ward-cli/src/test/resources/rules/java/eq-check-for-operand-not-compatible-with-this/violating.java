class Name {
  String v;

  @Override
  public boolean equals(Object o) {
    if (o instanceof Name) {
      return true;
    }
    return o instanceof String;
  }
}
