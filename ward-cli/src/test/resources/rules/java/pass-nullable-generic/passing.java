import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Calls {
  void take(List<@Nullable String> names) {}

  void run(List<@Nullable String> maybe, List<String> plain) {
    take(maybe);
    take(plain);
    take(new ArrayList<String>());
  }
}
