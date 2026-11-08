final class Check<U> {
  static final class Box<T extends Number> {}

  Box<? super U> field = null;
  Box<? super U> other;

  Box<? super U> make() {
    return null;
  }
}
