final class Check {
  private int size;

  class Inner {
    private final int copy;

    Inner() {
      copy = size;
    }

    int copy() {
      return copy;
    }
  }
}
