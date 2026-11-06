package dev.ward.analysis.domain.types;

import java.util.List;
import java.util.Objects;

/**
 * What a type declaration is and what it extends or implements.
 *
 * @param kind the kind of declaration
 * @param isAbstract whether it is abstract (an interface is)
 * @param isFinal whether it is final
 * @param isSealed whether it is sealed
 * @param supertypes the direct superclass and interfaces that resolved
 */
public record TypeShape(
    Kind kind, boolean isAbstract, boolean isFinal, boolean isSealed, List<TypeRef> supertypes) {

  /** The kinds of type declaration. */
  public enum Kind {
    /** A class. */
    CLASS,
    /** An interface. */
    INTERFACE,
    /** An enum. */
    ENUM,
    /** A record. */
    RECORD,
    /** An annotation type. */
    ANNOTATION
  }

  /**
   * Copies the supertypes.
   *
   * @throws NullPointerException if the kind or supertypes are missing
   */
  public TypeShape {
    Objects.requireNonNull(kind, "kind");
    supertypes = List.copyOf(supertypes);
  }
}
