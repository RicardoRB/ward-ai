package dev.ward.analysis.domain.scope;

/** How a name reference uses the binding it names. */
public enum ReferenceAccess {
  /** The value is read. */
  READ,
  /** The name is assigned or declared with a value. */
  WRITE,
  /** The name is deleted ({@code del x}). */
  DELETE
}
