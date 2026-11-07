class Violating {
  Object run() {
    Object result = make();
    return result;
  }
  Object fail() {
    RuntimeException ex = new RuntimeException();
    throw ex;
  }
  Object make() {
    return null;
  }
}
