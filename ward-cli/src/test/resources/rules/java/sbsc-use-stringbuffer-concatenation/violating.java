class Concat {
  String bad(int n) {
    String s = "";
    for (int i = 0; i < n; i++) {
      s += "x";
    }
    return s;
  }
}
