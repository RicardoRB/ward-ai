package dev.ward.analysis.domain.types;

import java.util.List;
import java.util.Objects;

/**
 * The fields and methods a type declaration inherits from its supertypes, with the supertypes of
 * {@code java.lang.Object} included. Private members are left out, since a subtype cannot see them.
 *
 * @param fields the inherited fields
 * @param methods the inherited methods, as the call targets that name them
 */
public record InheritedMembers(List<Field> fields, List<CallTarget> methods) {

  /** No inherited members known. */
  public static final InheritedMembers NONE = new InheritedMembers(List.of(), List.of());

  /**
   * An inherited field.
   *
   * @param declaringType the supertype that declares the field
   * @param name the name of the field
   * @param isStatic whether the field is static
   */
  public record Field(TypeRef declaringType, String name, boolean isStatic) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the declaring type or the name is missing
     */
    public Field {
      Objects.requireNonNull(declaringType, "declaringType");
      Objects.requireNonNull(name, "name");
    }
  }

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is missing
   */
  public InheritedMembers {
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
  }
}
