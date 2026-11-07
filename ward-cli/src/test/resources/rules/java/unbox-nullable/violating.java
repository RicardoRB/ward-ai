import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Counts {
  int first(@Nullable Integer count) {
    int value = count;
    return value;
  }

  int second(@Nullable Integer count) {
    return count;
  }
}
