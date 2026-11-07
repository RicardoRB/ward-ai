import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Arrays {
  private @Nullable String[] names = new String[2];

  void first(@Nullable String[] values, String single) {
    values[0] = null;
    names[1] = null;
    single = null;
  }

  void second() {
    @Nullable String[] local = new String[1];
    local[0] = null;
  }
}
