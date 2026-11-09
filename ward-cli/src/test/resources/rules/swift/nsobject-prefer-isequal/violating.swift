class C: NSObject {
  static func == (lhs: C, rhs: C) -> Bool { true }
}
@objcMembers class D {
  static func == (lhs: D, rhs: D) -> Bool { true }
}
