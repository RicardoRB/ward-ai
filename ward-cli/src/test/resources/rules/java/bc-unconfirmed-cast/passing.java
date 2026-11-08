class CastsOk {
  String guarded(Object value) {
    if (value instanceof String) {
      return (String) value;
    }
    return "";
  }

  Object upcast(String value) {
    return (Object) value;
  }

  String unknown(Object value) {
    return (String) value;
  }
}
