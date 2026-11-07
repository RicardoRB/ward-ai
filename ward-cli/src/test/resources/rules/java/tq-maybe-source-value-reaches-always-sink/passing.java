import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class Flows {
  void take(@NonNull String value) {}

  void run(@Nullable String maybe) {
    if (maybe != null) {
      take(maybe);
    }
  }

  @NonNull String give(@Nullable String maybe) {
    return maybe == null ? "" : maybe;
  }
}
