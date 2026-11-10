struct S {
  let a: Int
  var b: String
  init(a: Int, c: String) {
    self.a = a
    self.b = c
  }
}
struct T {
  let a: Int = 0
  init(a: Int) {
    self.a = a
  }
}
struct U {
  let a: Int
  init?(a: Int) {
    self.a = a
  }
}
