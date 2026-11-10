class Base {
  @com.google.errorprone.annotations.ForOverride
  protected void hook() {}

  void own() {
    hook();
  }
}

class Other {
  void run(Base base) {
    base.hook();
  }
}
