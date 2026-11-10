class CastsOk {
  String guarded(java.util.function.Supplier<Object> supplier) {
    if (supplier.get() instanceof String) {
      return (String) supplier.get();
    }
    return "";
  }

  String unknown(java.util.function.Supplier<Object> supplier) {
    return (String) supplier.get();
  }

  String variable(Object value) {
    return (String) value;
  }
}
