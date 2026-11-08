class A {
  static B b = new B();
}

class B {
  static int count = 1;
}

class C {
  B instance = new B();
}
