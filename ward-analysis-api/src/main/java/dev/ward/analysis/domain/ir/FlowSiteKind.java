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
  PARAMETER
}
