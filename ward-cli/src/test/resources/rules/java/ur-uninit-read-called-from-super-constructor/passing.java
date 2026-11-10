class Base {
  Base() {
    init();
  }

  void init() {
  }
}

class Child extends Base {
  private int size;

  void init() {
    size = 1;
    int copy = size;
  }
}
