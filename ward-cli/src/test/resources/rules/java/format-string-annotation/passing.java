







class Sample {
  static void fmt(@FormatString String f, Object... args) {}

  void run(String name) {
    fmt("hi");
    fmt("a" + "b");
    fmt(name);
  }
}
