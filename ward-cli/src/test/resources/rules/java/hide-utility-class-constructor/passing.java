class Plain {
  private Plain() {
  }

  static int twice(int value) {
    return value * 2;
  }
}

class Shape {
  int sides = 4;
}

class Failure extends RuntimeException {
  static final long CODE = 1L;
}

class Outer {
  private static final class Holder {
    static final int VALUE = 1;
  }

  static final int LIMIT = 2;

  class Inner {}
}
