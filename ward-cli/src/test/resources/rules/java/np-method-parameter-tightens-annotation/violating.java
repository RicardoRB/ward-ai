import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class Base {
  void accept(@Nullable String text) {}
}

class Child extends Base {
  void accept(@Nonnull String text) {}
}

