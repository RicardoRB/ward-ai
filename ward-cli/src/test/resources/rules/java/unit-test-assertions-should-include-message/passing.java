final class AssertionsTest {
  void checks(int value) {
    assertEquals(3, value, "value");
    assertTrue(value > 0, "positive");
  }
}
