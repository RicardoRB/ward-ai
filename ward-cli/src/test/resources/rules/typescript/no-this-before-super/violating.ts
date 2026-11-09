class A extends B {
  constructor() {
    this.x = 1;
    super();
  }
}
class C extends D {
  constructor() {
    super.foo();
    super();
  }
}
