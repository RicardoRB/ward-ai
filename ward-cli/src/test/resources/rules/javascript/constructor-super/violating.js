class A extends B {
  constructor() {
    this.x = 1;
  }
}
class C extends D {
  constructor() {
    super();
  }
}
class E extends F {
  constructor(c) {
    if (c) {
      super();
    }
  }
}
class G extends H {
  constructor() {
    super();
    super();
  }
}
