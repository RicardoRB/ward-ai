final class Check {
  void drop(java.util.List<String> names) {
    assert names.contains("a");
  }

  void checked(java.util.List<String> names) {
    assertTrue(names.remove("a"));
    assertEquals("b", new StringBuilder().append("b").toString());
  }
}
