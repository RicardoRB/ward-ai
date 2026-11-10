final class Check {
  private int size;

  class Uses {
    int size() {
      return size;
    }
  }

  class Never {
    int one() {
      return 1;
    }
  }

  static class Nested {
    Nested() {}
  }
}
