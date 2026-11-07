final class Violating {
  Object run() {
    return Foo.getDescriptor().findFieldByNumber(Bar.ID_FIELD_NUMBER);
  }
}
