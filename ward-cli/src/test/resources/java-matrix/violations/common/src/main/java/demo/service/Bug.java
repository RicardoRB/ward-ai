package demo.service;

/** Defines equals without hashCode, and dereferences a null. */
public final class Bug {
  private final int value;

  /**
   * Creates the value.
   *
   * @param value the value
   */
  public Bug(final int value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof Bug && ((Bug) other).value == value;
  }

  /**
   * Always fails.
   *
   * @return never
   */
  public int length() {
    final String text = null;
    return text.length();
  }
}
