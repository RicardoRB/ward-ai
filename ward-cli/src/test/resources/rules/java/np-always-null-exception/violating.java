class Strings {
  int run() {
    String s = null;
    try {
      risky();
    } catch (RuntimeException e) {
      return s.length();
    }
    return 0;
  }

  void risky() {}
}
