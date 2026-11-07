final class Passing {
  void run(boolean ok, int a) {


    Preconditions.checkArgument(ok, "bad %s", a);
  }
}
