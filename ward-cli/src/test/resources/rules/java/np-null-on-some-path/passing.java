class Strings {
  int first(boolean flag) {
    String s = "a";
    if (flag) {
      s = null;
    }
    if (s != null) {
      return s.length();
    }
    return 0;
  }

  int second(boolean flag, String other) {
    String t = other;
    if (flag) {
      t = null;
    }
    return t.length();
  }

  int third(boolean flag) {
    String u = null;
    if (flag) {
      u = "b";
    } else {
      u = "c";
    }
    return u.length();
  }
}
