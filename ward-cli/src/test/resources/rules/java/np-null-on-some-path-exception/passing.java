class Strings {
  int run() {
    String s = "a";
    try {
      s = "b";
      risky();
    } catch (RuntimeException e) {
      return s.length();
    }
    return 0;
  }

  void risky() {}
}
