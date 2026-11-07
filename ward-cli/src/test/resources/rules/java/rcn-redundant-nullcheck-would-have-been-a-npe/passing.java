class Checks {
  int first(String s) {
    if (s == null) {
      return 0;
    }
    return s.length();
  }

  int second(String s, String t) {
    s.trim();
    return t != null ? 1 : 0;
  }
}
