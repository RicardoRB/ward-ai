final class Passing {
  Object run() {
    return Foo.getDescriptor().findFieldByNumber(Foo.ID_FIELD_NUMBER);
  }
}
