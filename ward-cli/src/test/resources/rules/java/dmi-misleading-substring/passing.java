class SubstringsOk {
  String good(String s, Mystery m) {
    String tail = s.substring(1);
    return m.substring(0) + tail;
  }
}
