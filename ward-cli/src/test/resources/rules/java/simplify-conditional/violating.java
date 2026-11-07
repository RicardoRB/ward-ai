final class Types {
  void check(Object value) {
    if (value != null && value instanceof String) {
      System.out.println(value);
    }
  }
}
