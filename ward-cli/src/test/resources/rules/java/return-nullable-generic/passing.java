import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Returns {
  List<@Nullable String> first(List<String> plain) {
    return plain;
  }

  List<String> second(List<String> plain) {
    return plain;
  }
}
