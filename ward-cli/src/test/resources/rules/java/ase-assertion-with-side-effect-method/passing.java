final class Check {
  void drop(java.util.List<String> names) {
    assert names.contains("a");
  }

  void checked(java.util.List<String> names) {
    assertTrue(names.remove("a"));
    assertEquals("b", new StringBuilder().append("b").toString());
  }

  private static void assertEquals(final Object expected, final Object actual) {
    if (!expected.equals(actual)) {
      throw new IllegalStateException();
    }
  }

  private static void assertTrue(final boolean condition) {
    if (!condition) {
      throw new IllegalStateException();
    }
  }
}
