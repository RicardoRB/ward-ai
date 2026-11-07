class Violating {
  @InlineMe(replacement = "this.bar()")
  @Deprecated
  void foo() {
    log();
    bar();
  }
  void bar() {
  }
  void log() {
  }
}
