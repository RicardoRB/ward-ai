final class Check {
  int test(Object value) {
    int hash = value.hashCode();
    if (value == null) {
      return 0;
    }
    return hash;
  }
}
