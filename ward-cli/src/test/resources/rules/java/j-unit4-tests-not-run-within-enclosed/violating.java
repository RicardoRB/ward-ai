@RunWith(Enclosed.class)
class Violating {
  @Test
  public void direct() {
  }

  static class Nested {
    @Test
    public void inner() {
    }
  }
}
