struct Holder {
  var value: Int {
    mutating get { 1 }
    nonmutating set { }
  }
  var inline: Int {
    get { 1 } set { _ = newValue }
  }
  var split: Int {
    get {
      1
    } set(newer) {
      _ = newer
    }
  }
  subscript<Key>(_ key: Key.Type) -> Int where Key: Hashable {
    mutating get { 0 }
    nonmutating set { }
  }
}
