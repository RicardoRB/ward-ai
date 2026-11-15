class Buffers {
  StringBuffer a(String name) {
    StringBuffer sb = new StringBuffer("tmp = ");
    sb.append(name);
    return sb;
  }
  void c(Edit edit, String x) {
    edit.append("\n" + x);
  }
}
