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

class Outer {
  class Inner {}
}

class Sub extends Outer.Inner {
  Sub(final Outer outer) {
    outer.super();
  }
}
