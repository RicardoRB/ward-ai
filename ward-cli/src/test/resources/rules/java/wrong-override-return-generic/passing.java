import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Base {
  List<@Nullable String> read() {
    return null;
  }
}

@NullMarked
class Child extends Base {
  List<String> read() {
    return null;
  }
}
