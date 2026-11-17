final class SizeTest {
  void checks(int size) {
    assertEquals(3, size);
    assertTrue(size > 0);
    assertNotNull(name);
  }
}

final class JoinedTest {
  void checks(int a, int b, int c) {
    assertTrue(a != b || a != c);
    assertTrue(a == b && b != c);
  }
}
