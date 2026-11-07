import javax.annotation.Nonnull;

class Flows {
  void take(@Nonnull String value) {}

  void run(@Null String never, @Nonnull String always) {
    take(always);
    Object ignored = never;
  }
}
