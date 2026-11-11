class Base {
  Base() {}

  Base(int value) {}
}

class Child extends Base {
  Child() {
    super();
  }

  Child(int value) {
    super(value);
  }
}
