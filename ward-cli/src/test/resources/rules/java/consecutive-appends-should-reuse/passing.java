class Joiner {
  String a(String foo) {
    StringBuffer buf = new StringBuffer();
    buf.append("Hello").append(foo).append("World");
    StringBuffer other = new StringBuffer();
    other.append(foo);
    return buf.toString();
  }
}
