class Instances {
  boolean first(Object other) {
    Object o = other;
    return o instanceof String;
  }

  boolean second() {
    Object o = "text";
    return o instanceof String;
  }
}
