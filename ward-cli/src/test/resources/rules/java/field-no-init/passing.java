import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Plain {
  private String name;
  private static String shared;
  private @Nullable String other;
  @Inject private String injected;

  {
    name = "x";
  }
}

@NullMarked
class WithConstructor {
  private String name;

  WithConstructor() {
    this.name = "";
  }
}
