extension String {
  fileprivate func Something(){}
}
class MyClass {
  fileprivate let myInt = 4
}
struct Outer {
  struct Inter {
    fileprivate struct Inner {}
  }
}
class MyClass2 {
  fileprivate(set) var myInt = 4
}
