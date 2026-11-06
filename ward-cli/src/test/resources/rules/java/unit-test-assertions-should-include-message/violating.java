final class AssertionsTest {
  void checks(int value) {
    assertEquals(3, value);
    assertTrue(value > 0);
    assertEquals(3, value, "value");
  }
}
