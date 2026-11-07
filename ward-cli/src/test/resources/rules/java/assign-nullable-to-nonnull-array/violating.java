import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Arrays {
  private String[] names = new String[2];

  void first(String[] values) {
    values[0] = null;
  }

  void second() {
    String[] local = new String[1];
    local[0] = null;
    names[1] = null;
  }
}
