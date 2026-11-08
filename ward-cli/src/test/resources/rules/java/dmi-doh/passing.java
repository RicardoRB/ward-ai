// passes
final class Check {
  Object one(Object o) {
    return com.google.common.base.Preconditions.checkNotNull(o, "o");
  }
  Object swapped() {
    return com.google.common.base.Preconditions.checkNotNull("o", new Object());
  }
  void three(Object o) {
    org.junit.Assert.assertNotNull("message", o);
  }
  String local(String s) {
    return other.Strings.nullToEmpty("name");
  }
}
