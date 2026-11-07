import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
interface Source {
  String read();

  @Nullable String peek();
}

@NullMarked
class File implements Source {
  public @Nullable String read() {
    return null;
  }

  public @Nullable String peek() {
    return null;
  }
}
