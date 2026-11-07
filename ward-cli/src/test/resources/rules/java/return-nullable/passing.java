import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Names {
  private @Nullable String cached;

  @Nullable String first(@Nullable String name) {
    return name;
  }

  String second(@Nullable String name) {
    if (name == null) {
      return "";
    }
    return name;
  }

  int third() {
    return 0;
  }

  String fourth() {
    final Runnable run = () -> { return; };
    return "x";
  }
}

class Unmarked {
  String value() {
    return null;
  }
}
