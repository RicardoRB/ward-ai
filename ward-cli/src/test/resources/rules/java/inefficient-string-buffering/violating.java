class Buffers {
  StringBuffer a(String name) {
    return new StringBuffer("tmp = " + name);
  }
  void b(StringBuilder sb, String x) {
    sb.append(x + "!");
  }
}
