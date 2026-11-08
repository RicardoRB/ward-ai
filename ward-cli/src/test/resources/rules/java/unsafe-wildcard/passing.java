// passes
final class Check<U> {
  static final class Box<T extends Number> {}
  static final class Any<T> {}

  Box<? super Integer> field = null;
  Box<? extends U> other = null;
  Any<? super U> free = null;
  Box<? super U> made = new Box<>();

  Box<?> make() {
    return null;
  }
}
