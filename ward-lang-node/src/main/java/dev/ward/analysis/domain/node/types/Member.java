package dev.ward.analysis.domain.node.types;

import java.util.List;

/**
 * A property or method of an interface, class, enum or object type.
 *
 * @param name the member name; {@code ()} for a call signature and {@code []} for an index
 *     signature, whose type is the value type
 * @param type the property type, or the function type of the first overload of a method
 * @param sigs the overloads of a method, empty for a property
 * @param flags what the member declares
 * @param decorators the decorator names, without {@code @}
 */
public record Member(String name, Ty type, List<Sig> sigs, int flags, List<String> decorators) {
  /** The member is optional ({@code ?}). */
  public static final int OPTIONAL = 1;

  /** The member is static. */
  public static final int STATIC = 1 << 1;

  /** The member is abstract. */
  public static final int ABSTRACT = 1 << 2;

  /** The member is read-only. */
  public static final int READONLY = 1 << 3;

  /** The member is private or protected. */
  public static final int HIDDEN = 1 << 4;

  /** Copies the lists. */
  public Member {
    sigs = List.copyOf(sigs);
    decorators = List.copyOf(decorators);
  }

  /** Returns whether the member has {@code flag}. */
  public boolean has(final int flag) {
    return (flags & flag) != 0;
  }

  /** Returns whether the member is a method. */
  public boolean isMethod() {
    return !sigs.isEmpty();
  }
}
