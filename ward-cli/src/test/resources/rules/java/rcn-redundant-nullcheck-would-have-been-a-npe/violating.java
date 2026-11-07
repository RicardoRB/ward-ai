class Checks {
  int first(String s) {
    int length = s.length();
    if (s == null) {
      return 0;
    }
    return length;
  }

  int second(String s) {
    s.trim();
    return s != null ? 1 : 0;
  }
}
