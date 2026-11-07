import org.jspecify.annotations.Nullable;

class Stores {
  @Nullable String maybe;
  String plain;

  void first() {
    maybe = null;
    plain = null;
    this.maybe = null;
  }
}
