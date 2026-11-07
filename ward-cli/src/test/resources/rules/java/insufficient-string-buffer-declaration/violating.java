class Sizes {
  String a() {
    StringBuilder bad = new StringBuilder();
    bad.append("This is a long string that will exceed the default 16 characters");
    return bad.toString();
  }
}
