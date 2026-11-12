package dev.ward.analysis.domain.api;

import java.util.List;
import java.util.Objects;

/**
 * A type that is part of an API, with its API members only.
 *
 * @param name the qualified name; a nested type is written {@code Outer.Inner}
 * @param kind what the type is
 * @param visibility who can see it
 * @param isFinal whether it cannot be extended
 * @param isSealed whether only permitted types can extend it
 * @param isAbstract whether it cannot be instantiated
 * @param typeParameters its type parameters
 * @param supertypes the direct superclass and interfaces
 * @param members its API methods, constructors and fields
 * @param location where it is declared
 */
public record ApiType(
    String name,
    Kind kind,
    ApiVisibility visibility,
    boolean isFinal,
    boolean isSealed,
    boolean isAbstract,
    List<ApiTypeParameter> typeParameters,
    List<ApiTypeRef> supertypes,
    List<ApiMember> members,
    ApiLocation location) {

  /** What a type is. */
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
    ANNOTATION,
    /** A singleton object. */
    OBJECT
  }

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a required field is missing
   */
  public ApiType {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(location, "location");
    typeParameters = List.copyOf(typeParameters);
    supertypes = List.copyOf(supertypes);
    members = List.copyOf(members);
  }

  /**
   * Returns whether code outside the type's package can extend or implement it: an interface can,
   * and a class can unless it is final or sealed or has no constructor to call.
   */
  public boolean extendable() {
    if (isFinal || isSealed) {
      return false;
    }
    return switch (kind) {
      case INTERFACE -> true;
      case CLASS -> members.stream().anyMatch(ApiType::callableByExtenders);
      default -> false;
    };
  }

  private static boolean callableByExtenders(final ApiMember member) {
    return member.kind() == ApiMember.Kind.CONSTRUCTOR
        && (member.visibility() == ApiVisibility.PUBLIC
            || member.visibility() == ApiVisibility.PROTECTED);
  }

  /**
   * Returns whether the type itself can be seen outside its package, ignoring its enclosing type.
   */
  public boolean isVisible() {
    return visibility == ApiVisibility.PUBLIC || visibility == ApiVisibility.PROTECTED;
  }

  /** Returns whether {@code member} of this type is part of its API. */
  public boolean exposes(final ApiMember member) {
    return member.visibility() == ApiVisibility.PUBLIC
        || member.visibility() == ApiVisibility.PROTECTED && extendable();
  }
}
