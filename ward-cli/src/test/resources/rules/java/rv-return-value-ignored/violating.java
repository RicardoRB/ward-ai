final class Check {
  void test(String name) {
    name.trim();
  }

  void pick(int mode, String name) {
    switch (mode) {
      case 1 -> name.trim();
      default -> { }
    }
  }
}
