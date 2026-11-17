class Passing {
  Object run() {
    Object result = make();
    log();
    return result;
  }
  Object other() {
    return make();
  }
  Object make() {
    return null;
  }
  void log() {
  }
}
class Annotated {
  java.util.List<String> cast(Object value) {
    @SuppressWarnings("unchecked")
    final java.util.List<String> list = (java.util.List<String>) value;
    return list;
  }
}
