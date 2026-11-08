// passes
final class Check {
  @javax.annotation.Nonnull
  String name;
  @javax.annotation.Nonnull
  String label = "x";
  @javax.annotation.Nonnull
  final String fixed;
  String plain;

  Check() {
    this.name = "x";
    this.fixed = "y";
  }

  Check(int size) {
    this();
  }
}
