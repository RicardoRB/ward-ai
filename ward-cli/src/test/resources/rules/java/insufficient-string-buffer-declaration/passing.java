class Sizes {
  String a() {
    StringBuilder good = new StringBuilder(41);
    good.append("This is a long string, which is pre-sized");
    StringBuilder tiny = new StringBuilder();
    tiny.append("abc");
    return good.toString();
  }
}
