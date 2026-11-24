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
  QUERY_PART,
  /**
   * A call that sets the content type of a response to JSON, such as {@code
   * res.setHeader('Content-Type', 'application/json')}: the name is {@code json} and the qualifier
   * is the response as written.
   */
  CONTENT_TYPE,
  /**
   * One use of a value that a condition of the body proved with a validator function, such as the
   * {@code next} of {@code redirect(next)} under {@code if is_safe(next)}: the name is {@code
   * <what>:<function>@<index>}, the annotation {@code ward:arity:<n>} gives the arity of the
   * validator, and the argument is the span of the use. A specification trusts the site only for a
   * validator whose body proves what {@code <what>} says.
   */
  VALIDATED
}
