import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Inference {
  void run() {
    List<@Nullable String> names = List.of("a", null);
    List<String> plain = List.of("a", "b");
    var untyped = List.of("a", null);
  }
}
