import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Counts {
  int first(@Nullable Integer count) {
    int value = count == null ? 0 : count;
    return value;
  }

  Integer second(@Nullable Integer count) {
    Integer value = count;
    return value;
  }
}
