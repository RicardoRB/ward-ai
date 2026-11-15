final class ParserTest {
  void rejectsEmpty() {
    assertThrows(IllegalArgumentException.class, () -> parse(""));
  }
}

final class Depth {
  int depth;
  int enter(Cursor cursor) {
    try {
      if (depth > 3) {
        throw cursor.fail("too deep");
      }
      return 1;
    } finally {
      depth--;
    }
  }
  interface Cursor {
    RuntimeException fail(String message);
  }
}
