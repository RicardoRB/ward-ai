final class AnnotationLocationViolations {
  @Deprecated(since = "1") void old() {}
  @SuppressWarnings("x") void other() {}
  @Override public String toString() { return ""; }
  @Deprecated
  void fine() {}
}
