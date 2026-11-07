import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Base {
  void accept(String value, @Nullable String other) {}
}

@NullMarked
class Child extends Base {
  void accept(@Nullable String value, @Nullable String other) {}
}
