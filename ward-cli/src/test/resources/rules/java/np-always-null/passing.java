class Strings {
  int first(String param) {
    return param.length();
  }

  int second() {
    String s = null;
    s = "a";
    return s.length();
  }

  int third() {
    String s = null;
    if (s == null) {
      return 0;
    }
    return s.length();
  }

  int fourth(boolean flag) {
    String t = null;
    if (flag) {
      t = "x";
    }
    return flag ? t.length() : 0;
  }

  int fifth(String unknown) {
    String t = unknown;
    return t.length();
  }
}
