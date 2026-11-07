import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Holder {
  private String name = "";
  private @Nullable String other;

  void first(@Nullable String value) {
    this.name = value;
  }

  void second() {
    name = null;
  }

  void third() {
    name = other;
  }
}
