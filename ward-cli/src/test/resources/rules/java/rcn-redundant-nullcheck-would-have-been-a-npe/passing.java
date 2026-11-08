// passes
final class Check {
  int test(Object value) {
    if (value == null) {
      return 0;
    }
    return value.hashCode();
  }
}
