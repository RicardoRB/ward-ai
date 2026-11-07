import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Returns {
  @Nullable String first() {
    return null;
  }

  int second() {
    return 0;
  }
}

class Unmarked {
  String first() {
    return null;
  }
}
