import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Calls {
  Calls(String label) {}

  void take(String value, @Nullable String other) {}

  void run(@Nullable String maybe) {
    take(null, null);
    this.take(maybe, null);
    new Calls(null);
  }
}
