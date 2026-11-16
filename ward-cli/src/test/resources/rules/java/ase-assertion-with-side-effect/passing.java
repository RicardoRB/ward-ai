final class Check {
  void bump(int count) {
    assert count > 0;
  }

  void checked(int count, int[] slots) {
    assertEquals(4, slots[count = 2]);
    assertTrue(count++ > 0);
  }

  private static void assertEquals(final int expected, final int actual) {
    if (expected != actual) {
      throw new IllegalStateException();
    }
  }

  private static void assertTrue(final boolean condition) {
    if (!condition) {
      throw new IllegalStateException();
    }
  }
}
