final class Check {
  Object one() {
    return com.google.common.base.Preconditions.checkNotNull("o");
  }
  String two() {
    return com.google.common.base.Strings.nullToEmpty("name");
  }
  void three() {
    org.junit.Assert.assertNotNull("value");
  }
  void four() {
    org.junit.Assert.assertNotNull("message", "value");
  }
}
