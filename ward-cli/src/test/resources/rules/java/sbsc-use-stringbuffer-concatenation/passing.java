class ConcatOk {
  String good(int n) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < n; i++) {
      s.append("x");
    }
    return s.toString();
  }
}
