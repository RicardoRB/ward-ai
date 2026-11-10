final class Check {
  Runnable make() {
    return new Runnable() {
      public void run() {
        System.out.println("run");
      }
      void helper() {}
    };
  }
}
