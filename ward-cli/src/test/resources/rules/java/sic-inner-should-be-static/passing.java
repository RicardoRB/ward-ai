final class T {
  private int value;

  class UsesField {
    int get() {
      return value;
    }
  }

  static class Nested {}

  class UsesThis {
    Object outer() {
      return T.this;
    }
  }
}

final class U extends Base {
  class Unknown {}
}
