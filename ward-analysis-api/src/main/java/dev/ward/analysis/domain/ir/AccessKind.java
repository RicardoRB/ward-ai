package dev.ward.analysis.domain.ir;

/** How a basic block touches a local variable or parameter. */
public enum AccessKind {
  /** A declaration. It is not a write by itself; an initializer adds a {@link #WRITE}. */
  DECLARE,
  /** An assignment, or the initializer of a declaration. */
  WRITE,
  /** The incoming value of a parameter, a write at the entry of the body. */
  PARAMETER,
  /** A read of the variable's value. */
  READ,
  /** A nested function body, created in this block, that reads or writes the variable. */
  CAPTURE
}
