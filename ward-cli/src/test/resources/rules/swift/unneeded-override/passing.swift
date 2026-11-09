class A: B {
  override func foo(a: Int) {
    super.foo(a: a + 1)
  }
  override func bar(a: Int) {
    print(a)
    super.bar(a: a)
  }
  @objc override func baz(a: Int) {
    super.baz(a: a)
  }
  override func qux(a: Int = 1) {
    super.qux(a: a)
  }
}
