final class Check {
  void use(String value) {
    value.length();
  }

  void test() {
    use(null);
  }
}
