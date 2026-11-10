import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Base {
  void accept(@Nullable String value, int count) {}
}

@NullMarked
class Child extends Base {
  void accept(String value, int count) {}
}
