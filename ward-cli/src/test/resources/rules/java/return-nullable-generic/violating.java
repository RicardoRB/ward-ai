import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Returns {
  List<String> first(List<@Nullable String> maybe) {
    return maybe;
  }

  List<String> second() {
    return new ArrayList<@Nullable String>();
  }
}
