import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Calls {
  void take(List<String> names) {}

  void run(List<@Nullable String> maybe) {
    take(maybe);
    take(new ArrayList<@Nullable String>());
  }
}
