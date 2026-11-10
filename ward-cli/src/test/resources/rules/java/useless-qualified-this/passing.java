class Passing {
  Runnable r = new Runnable() {
    public void run() {
      go(Passing.this);
    }
  };
  class Inner {
    Passing outer = Passing.this;
  }
  void go(Passing p) {
  }
}
