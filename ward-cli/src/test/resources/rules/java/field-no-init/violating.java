import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Plain {
  private String name;
  private @Nullable String other;
  private String ready = "";
  private int count;
}
