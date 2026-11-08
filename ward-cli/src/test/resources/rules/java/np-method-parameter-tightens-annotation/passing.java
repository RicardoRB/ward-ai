import javax.annotation.Nonnull;
import javax.annotation.CheckForNull;

class Base {
  void accept(@CheckForNull String text) {}
}

class Child extends Base {
  void accept(@CheckForNull String text) {}
}

