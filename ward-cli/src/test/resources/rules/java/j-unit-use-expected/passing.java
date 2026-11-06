final class ParserTest {
  void rejectsEmpty() {
    assertThrows(IllegalArgumentException.class, () -> parse(""));
  }
}
