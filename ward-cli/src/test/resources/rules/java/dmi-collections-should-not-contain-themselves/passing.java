final class Bag {
  void fill(java.util.List<Object> list, java.util.List<Object> other) {
    list.add(other);
  }
}

final class Amounts {
  Object twice(Fraction value) {
    return value.add(value);
  }
}

final class Fraction {
  Fraction add(Fraction other) {
    return other;
  }
}
