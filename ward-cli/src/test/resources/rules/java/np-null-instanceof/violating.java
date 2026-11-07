class Instances {
  boolean first() {
    Object o = null;
    return o instanceof String;
  }

  boolean second() {
    return null instanceof String;
  }
}
