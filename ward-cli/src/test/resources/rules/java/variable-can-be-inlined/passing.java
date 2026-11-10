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
