package dev.ward.analysis.domain.types;

/**
 * A type that another type is built from: one type argument of a parameterized type, or the element
 * type of an array type. A component that the resolver cannot read, such as a type variable, a
 * wildcard or a type that no class holds, has no type, shape or members.
 *
 * @param type the erased type, or {@code null} when it is unknown
 * @param shape what the type is, or {@code null} when it is unknown
 * @param members the supertypes of the type in {@code ancestors} and nothing else, or {@code null}
 *     when it is unknown
 */
public record TypeComponent(TypeRef type, TypeShape shape, TypeMembers members) {

  /** The component that could not be read. */
  public static final TypeComponent UNKNOWN = new TypeComponent(null, null, null);

  /** Tells whether the type, its shape and its supertypes are all known. */
  public boolean known() {
    return type != null && shape != null && members != null;
  }
}
