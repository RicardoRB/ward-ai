package dev.ward.analysis.domain.api;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A method, constructor or field that is part of an API. A constructor is named {@code <init>}.
 *
 * @param kind what the member is
 * @param name the member name
 * @param visibility who can see it
 * @param isStatic whether it belongs to the type, not to an instance
 * @param isFinal whether it cannot be overridden (method) or reassigned (field)
 * @param isAbstract whether it has no body and an implementer must provide one
 * @param isDefault whether it is an interface method with a body that implementers may override
 * @param isVarargs whether the last parameter is variadic
 * @param typeParameters the type parameters of a method
 * @param parameters the parameters of a method or constructor
 * @param type the return type of a method, the type of a field, {@code void} for a constructor
 * @param location where it is declared
 */
public record ApiMember(
    Kind kind,
    String name,
    ApiVisibility visibility,
    boolean isStatic,
    boolean isFinal,
    boolean isAbstract,
    boolean isDefault,
    boolean isVarargs,
    List<ApiTypeParameter> typeParameters,
    List<ApiParameter> parameters,
    ApiTypeRef type,
    ApiLocation location) {

  /** The constructor name. */
  public static final String CONSTRUCTOR = "<init>";

  /** What a member is. */
  public enum Kind {
    /** A method. */
    METHOD,
    /** A constructor. */
    CONSTRUCTOR,
    /** A field. */
    FIELD
  }

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a required field is missing
   */
  public ApiMember {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(location, "location");
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
  }

  /** Returns the identity of the member: its name and erased parameter types, or a field's name. */
  public String id() {
    if (kind == Kind.FIELD) {
      return name;
    }
    return name
        + parameters.stream()
            .map(parameter -> parameter.type().erasure())
            .collect(Collectors.joining(",", "(", ")"));
  }

  /** Returns the member as a signature for messages, such as {@code String name(int)}. */
  public String signature() {
    if (kind == Kind.FIELD) {
      return type.text() + " " + name;
    }
    final String arguments =
        parameters.stream()
            .map(parameter -> parameter.type().text())
            .collect(Collectors.joining(", "));
    return kind == Kind.CONSTRUCTOR
        ? name + "(" + arguments + ")"
        : type.text() + " " + name + "(" + arguments + ")";
  }
}
