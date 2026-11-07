import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Calls {
  void take(String value) {}

  void maybe(@Nullable String value) {}

  void run(String text) {
    take(text);
    maybe(null);
    other(null);
  }
}

class Unmarked {
  void take(String value) {}

  void run() {
    take(null);
  }
}
