import javax.annotation.Nonnull;

class Flows {
  void take(@Nonnull String value) {}

  void run(@Null String never) {
    take(never);
  }
}
