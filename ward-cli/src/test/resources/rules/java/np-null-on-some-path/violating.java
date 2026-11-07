class Strings {
  int first(boolean flag) {
    String s = "a";
    if (flag) {
      s = null;
    }
    return s.length();
  }

  int second(boolean flag) {
    String t = null;
    if (flag) {
      t = "b";
    }
    return t.length();
  }
}
