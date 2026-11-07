class Violating {
  Violating self = Violating.this;
  class Inner {
    Violating outer = Violating.this;
    Inner me = Inner.this;
  }
}
