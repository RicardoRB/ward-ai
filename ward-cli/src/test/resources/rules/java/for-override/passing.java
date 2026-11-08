














class Base {
  @com.google.errorprone.annotations.ForOverride
  protected void hook() {}

  void own() {
    hook();
    this.hook();
  }
}

class Child extends Base {
  @Override
  protected void hook() {
    super.hook();
  }
}
