import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Holder {
  private String name = "";
  private @Nullable String other;

  void first(@Nullable String value) {
    if (value != null) {
      this.name = value;
    }
    other = value;
    other = null;
  }

  void second(@Nullable String name) {
    name = null;
  }
}
