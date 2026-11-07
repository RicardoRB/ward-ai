import org.jspecify.annotations.Nullable;

class Contracts {
  private @Nullable String name;

  @RequiresNonNull("name")
  void use() {}

  void first() {
    use();
  }

  void second() {
    this.use();
  }
}
