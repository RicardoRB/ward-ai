import org.jspecify.annotations.Nullable;

class Contracts {
  private @Nullable String name;
  private String other = "";

  @RequiresNonNull("name")
  void use() {}

  @RequiresNonNull("other")
  void useOther() {}

  void first() {
    if (name != null) {
      use();
    }
  }

  @RequiresNonNull("name")
  void second() {
    use();
  }

  void third() {
    useOther();
    helper.use();
  }
}
