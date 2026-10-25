package demo.service;

/** Describes request values with a pattern-matching switch. */
public final class Describer {
  /**
   * Describes a value.
   *
   * @param value what to describe
   * @return the description
   */
  public String describe(final Object value) {
    return switch (value) {
      case Integer number when number > 0 -> "positive";
      case Integer number -> "not positive";
      case String text -> "text " + text;
      default -> "unknown";
    };
  }
}
