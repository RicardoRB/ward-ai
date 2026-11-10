import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Switches {
  String first(int kind) {
    return switch (kind) {
      case 1 -> "one";
      default -> null;
    };
  }
}
