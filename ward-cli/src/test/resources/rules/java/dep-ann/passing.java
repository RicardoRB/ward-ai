
final class DeprecatedPassing {
  /** Old call.
   * @deprecated use run instead */
  @Deprecated void old() {}

  /* (not a Javadoc) the comment before this method is a plain block comment */
  void plain() {}

  /**
   * Reads the {@code @deprecated} notice of another method.
   */
  void reads() {}

  void bare() {}
}
