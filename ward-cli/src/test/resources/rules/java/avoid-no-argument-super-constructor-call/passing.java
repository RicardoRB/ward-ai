class Base {
  Base() {}

  Base(int value) {}
}

class Child extends Base {
  Child() {}

  Child(int value) {
    super(value);
  }
}
