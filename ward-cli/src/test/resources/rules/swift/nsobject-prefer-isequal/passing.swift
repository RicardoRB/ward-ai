struct S {
  static func == (lhs: S, rhs: S) -> Bool { true }
}
class C {
  static func == (lhs: C, rhs: C) -> Bool { true }
}
class E: NSObject {
  func isEqual(_ other: Any?) -> Bool { true }
}
