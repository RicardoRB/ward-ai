// passes
final class Check {
  private void use(String value) {
    if (value != null) {
      value.length();
    }
  }

  void test() {
    use(null);
    use("x");
  }
}
