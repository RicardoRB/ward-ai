import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Calls {
  Calls(String label) {}

  void take(String value, @Nullable String other) {}

  void take(String value, String other, String third) {}
  void take(String value, String other, @Nullable String third) {}

  void run(@Nullable String maybe) {
    take("a", null);
    if (maybe != null) {
      take(maybe, maybe);
    }
    take(null, "", "");
    other(null);
  }

  void other(@Nullable String value) {}
}
