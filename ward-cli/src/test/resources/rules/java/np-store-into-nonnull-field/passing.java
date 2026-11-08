// passes
final class Check {
  @javax.annotation.Nonnull
  String name = "";
  String other;

  void test() {
    this.name = "x";
    this.other = null;
  }
}
