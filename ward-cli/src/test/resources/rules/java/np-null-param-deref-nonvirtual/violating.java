final class Check {
  private void use(String value) {
    value.length();
  }

  void test() {
    use(null);
  }
}
