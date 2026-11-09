class A extends B {
  constructor() {}
}
class C extends D {
  constructor(value) {
    this.prop = value;
    super();
  }
}
class E extends F {
  constructor(cond) {
    if (cond) {
      super();
    }
  }
}
