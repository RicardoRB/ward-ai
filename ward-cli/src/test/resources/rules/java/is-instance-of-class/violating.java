final class Violating {
  boolean run(Class<?> lhs, Class<?> rhs) {
    return lhs.isInstance(rhs);
  }
}
