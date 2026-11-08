import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

class Base {
  @Nonnull String name() { return ""; }
}

class Child extends Base {
  String name() { return ""; }
}

