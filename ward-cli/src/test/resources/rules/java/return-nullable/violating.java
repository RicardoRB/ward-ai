import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Names {
  private @Nullable String cached;

  String first(@Nullable String name) {
    if (name == null) {
      return null;
    }
    return name;
  }

  String second(@Nullable String name) {
    return name;
  }

  String third() {
    return cached;
  }

  @Nullable String fourth() {
    return null;
  }
}
