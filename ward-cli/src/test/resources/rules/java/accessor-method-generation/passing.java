final class Outer {
  static final class Inner {
    int value() {
      return 1;
    }
  }

  int read(Inner inner) {
    return inner.value();
  }
}
