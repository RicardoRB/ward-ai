@FunctionalInterface
interface Greeter {
  void greet();
}

interface Named extends Runnable {
  void name();
}

interface Factories {
  @Deprecated
  static Greeter greeter() {
    return () -> {};
  }
}
