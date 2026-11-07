class Strings {
  int first() {
    String s = null;
    return s.length();
  }

  int second(boolean flag) {
    String t = "a";
    if (flag) {
      t = null;
    } else {
      t = null;
    }
    return t.length();
  }

  int third() {
    String u = null;
    u = null;
    return u.length() + u.hashCode();
  }
}
