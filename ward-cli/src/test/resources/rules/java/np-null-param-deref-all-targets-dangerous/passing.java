// passes
class Open {
  void use(String value) {
    value.length();
  }

  void test() {
    use(null);
  }
}
