import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Switches {
  @Nullable String first(int kind) {
    return switch (kind) {
      case 1 -> "one";
      default -> null;
    };
  }

  String second(int kind) {
    return switch (kind) {
      case 1 -> "one";
      default -> "many";
    };
  }
}
