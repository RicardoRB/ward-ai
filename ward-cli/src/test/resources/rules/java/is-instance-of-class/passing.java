final class Passing {
  boolean run(Class<?> lhs, Object rhs) {


    return lhs.isInstance(rhs);
  }
}
