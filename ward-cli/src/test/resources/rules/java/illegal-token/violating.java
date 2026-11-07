class Violating {
  void method() {
    outer:
    for (int i = 0; i < 2; i++) {
      break outer;
    }
  }
}
