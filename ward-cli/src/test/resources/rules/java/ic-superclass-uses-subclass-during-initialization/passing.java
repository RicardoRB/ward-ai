class Base {
  static final Base DEFAULT = new Base();
  Base other = new Derived();
}

class Derived extends Base {
  static final Base PARENT = new Base();
}
