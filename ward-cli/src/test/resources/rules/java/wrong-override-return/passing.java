import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
interface Source {
  @Nullable String read();

  String peek();
}

@NullMarked
class File implements Source {
  public @Nullable String read() {
    return null;
  }

  public String peek() {
    return "";
  }
}
