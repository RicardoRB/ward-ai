final class Compare {
  boolean differs(int a, int b) {
    return a != b && !(a == b && a > 0);
  }
  boolean notMap(Object value) {
    return !(value instanceof java.util.Map<?, ?> map) || map.isEmpty();
  }
}
