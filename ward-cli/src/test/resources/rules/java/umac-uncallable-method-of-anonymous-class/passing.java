// passes
final class Check {
  Runnable make() {
    return new Runnable() {
      public void run() { helper(); }
      void helper() {}
    };
  }
  Object plain() {
    return new Object() {
      void extra() {}
    };
  }
  Runnable priv() {
    return new Runnable() {
      public void run() {}
      private void hidden() {}
    };
  }
}
