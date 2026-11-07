import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Lists {
  List<@Nullable String> maybe = new ArrayList<>();

  void first() {
    List<String> names = new ArrayList<@Nullable String>();
    List<String> copy = maybe;
  }

  void second(List<String> target) {
    target = maybe;
  }
}
