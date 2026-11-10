import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Deref {
  private @Nullable String cached;

  int first(@Nullable String name) {
    if (name == null) {
      return 0;
    }
    return name.length();
  }

  int second(String name) {
    return name.length();
  }

  @Nullable String find() {
    return null;
  }

  int third() {
    final String found = find();
    return found == null ? 0 : found.length();
  }
}
