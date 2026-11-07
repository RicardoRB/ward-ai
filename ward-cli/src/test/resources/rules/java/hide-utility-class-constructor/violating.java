class Helpers {
  static int twice(int value) {
    return value * 2;
  }
}

final class Constants {
  static final int LIMIT = 3;
}

class Pair {
  private Pair() {
  }

  static Pair of() {
    return new Pair();
  }
}
