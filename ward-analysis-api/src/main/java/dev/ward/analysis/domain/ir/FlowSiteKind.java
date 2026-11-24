package dev.ward.analysis.domain.ir;

/** What a flow site of a basic block is. */
public enum FlowSiteKind {
  /** A method or function call. */
  CALL,
  /** A property or member read, such as {@code req.query}. */
  MEMBER_READ,
  /** A property write, such as {@code el.innerHTML = html}; argument 0 is the assigned value. */
  MEMBER_WRITE,
  /** A parameter, carrying its annotation or decorator names. */
  PARAMETER,
  /**
   * A string built from literal and value parts, such as {@code "<p>" + x}: the name is the literal
   * text and the arguments are the spans of the value parts.
   */
  STRING_BUILD,
  /**
   * A string literal that is a whole value: an initializer, an assigned value or a whole call
   * argument, with an optional {@code getBytes} or {@code toCharArray} conversion. The name is the
   * literal contents and the qualifier is {@code value} or {@code argument}.
   */
  LITERAL,
  /**
   * The value parts of a string built after a literal {@code ?}, such as {@code x} in {@code "/p?"
   * + x}: the name is {@code ?} and the arguments are the spans of those parts. They can only be
   * part of the query of a URL, never its scheme, host or path.
   */
  QUERY_PART
}
