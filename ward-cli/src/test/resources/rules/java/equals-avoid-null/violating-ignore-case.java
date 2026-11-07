final class Equality {
  boolean same(final String value) {
    return value.equals("ward");
  }

  boolean fold(final String value) {
    return value.equalsIgnoreCase("ward");
  }
}
