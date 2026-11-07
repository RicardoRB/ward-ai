import Foundation

struct Point {
  var x: Int
  var y: Int
}

final class Cache {
  var values: [String] = []
}

final class Store: Cache {
  func reset() {
    values = []
  }
}
