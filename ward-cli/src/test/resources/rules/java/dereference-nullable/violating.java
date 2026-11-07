import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Deref {
  private @Nullable String cached;

  int first(@Nullable String name) {
    return name.length();
  }

  int second() {
    return cached.length();
  }

  @Nullable String find() {
    return null;
  }

  int third() {
    return find().length();
  }
}
