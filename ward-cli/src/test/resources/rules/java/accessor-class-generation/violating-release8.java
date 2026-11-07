final class Outer {
  private static final class Inner {
    private Inner() {
    }
  }

  Object make() {
    return new Inner();
  }
}
