class Passing {
  @InlineMe(replacement = "this.bar()")
  @Deprecated
  void foo() {
    bar();
  }
  void bar() {
  }
}
