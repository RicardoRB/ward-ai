import org.jspecify.annotations.NonNull;

class Stores {
  @NonNull String name = "a";

  void first() {
    name = null;
  }

  void second() {
    this.name = null;
  }
}
