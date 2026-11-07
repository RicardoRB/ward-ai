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
public record Member(String name, TsType type, List<Sig> sigs, int flags, List<String> decorators) {
  /** The member is optional ({@code ?}). */
  public static final int OPTIONAL = 1;

  /** The member is static. */
  public static final int STATIC = OPTIONAL << 1;

  /** The member is abstract. */
  public static final int ABSTRACT = STATIC << 1;

  /** The member is read-only. */
  public static final int READONLY = ABSTRACT << 1;

  /** The member is private or protected. */
  public static final int HIDDEN = READONLY << 1;

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
