final class Check {
  void bump(int count) {
    assert count > 0;
  }

  void checked(int count, int[] slots) {
    assertEquals(4, slots[count = 2]);
    assertTrue(count++ > 0);
  }
}
