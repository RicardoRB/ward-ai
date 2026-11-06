final class ParserTest {
  void rejectsEmpty() {
    try {
      parse("");
      fail("expected an exception");
    } catch (IllegalArgumentException expected) {
      assertTrue(true);
    }
  }
}
