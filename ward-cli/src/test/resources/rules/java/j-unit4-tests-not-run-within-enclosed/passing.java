@RunWith(Enclosed.class)
class Passing {
  static class Nested {
    @Test
    public void inner() {
    }
  }
}
class Plain {
  @Test
  public void direct() {
  }
}
