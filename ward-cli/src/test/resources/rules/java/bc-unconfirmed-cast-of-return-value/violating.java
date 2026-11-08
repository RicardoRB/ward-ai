class Casts {
  String bad(java.util.function.Supplier<Object> supplier) {
    return (String) supplier.get();
  }
}
