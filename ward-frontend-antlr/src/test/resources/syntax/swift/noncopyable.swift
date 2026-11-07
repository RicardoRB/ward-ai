struct Token: ~Copyable {
  var id: Int
}

func consumeToken<T: ~Copyable>(_ value: borrowing T) {}
