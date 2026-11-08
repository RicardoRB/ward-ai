

















class Sample {
  String ok(int a, String s) {
    return String.format("%s %d %n%%", s, a);
  }

  String indexed(int a) {
    return String.format("%1$d %1$d", a);
  }

  String array(Object[] values) {
    return String.format("%s %s", values);
  }
}
