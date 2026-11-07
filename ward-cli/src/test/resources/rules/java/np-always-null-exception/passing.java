class Strings {
  int run() {
    String s = null;
    try {
      risky();
    } catch (RuntimeException e) {
      s = "x";
      return s.length();
    }
    return 0;
  }

  void risky() {}
}
