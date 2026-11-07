final class Violating {
  void run(boolean ok, int a) {
    Preconditions.checkArgument(ok, "bad %s and %s", a);
  }
}
