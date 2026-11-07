import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Base {
  void accept(List<String> values) {}
}

@NullMarked
class Child extends Base {
  void accept(List<@Nullable String> values) {}
}
