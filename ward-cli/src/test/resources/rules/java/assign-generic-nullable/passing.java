import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Lists {
  List<@Nullable String> maybe = new ArrayList<>();
  List<String> plain = new ArrayList<>();

  void first() {
    List<@Nullable String> names = new ArrayList<@Nullable String>();
    List<String> copy = plain;
    List<@Nullable String> wide = plain;
    List<String> fresh = new ArrayList<>();
  }
}
