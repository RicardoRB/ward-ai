class Sample {
  String missing(int a) {
    return String.format("%s %s", a);
  }

  String wrongType(String s) {
    return String.format("%d", s);
  }

  String extra(int a) {
    return String.format("%d", a, a);
  }

  String unknown(int a) {
    return String.format("%q", a);
  }
}
