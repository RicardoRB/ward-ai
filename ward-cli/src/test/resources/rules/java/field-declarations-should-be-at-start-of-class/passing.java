class Passing {
  static {
    System.out.println("init");
  }

  enum Mode {
    ON
  }

  int a;
  private int b;

  void run() {}

  Runnable task = new Runnable() {
    @Override
    public void run() {}
  };
}
