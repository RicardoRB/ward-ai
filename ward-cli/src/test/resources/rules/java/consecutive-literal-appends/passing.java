class Text {
  String a(StringBuilder buf, String foo) {
    buf.append("Hello World");
    buf.append(foo).append("x");
    return buf.toString();
  }
}
