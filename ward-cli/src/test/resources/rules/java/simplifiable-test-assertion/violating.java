final class SizeTest {
  void checks(int size) {
    assertTrue(size == 3);
    assertEquals(true, size > 0);
    assertTrue(!isEmpty());
  }
}
