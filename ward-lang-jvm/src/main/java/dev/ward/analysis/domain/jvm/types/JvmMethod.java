package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A method or constructor of a class; a constructor is named {@code <init>}.
 *
 * @param name the method name
 * @param access the access flags
 * @param typeParameters the declared type parameters
 * @param parameters the declared parameter types
 * @param returnType the declared return type, {@code void} for a constructor
 * @param thrown the declared exception types
 * @param annotations the qualified names of its annotations
 * @param parameterAnnotations the qualified names of the annotations of each parameter, when known
 * @param compatibleWith the {@code @CompatibleWith} value of each parameter, empty text for none
 * @param annotationValues the element values of its annotations, by {@code <annotation>#<element>}
 */
public record JvmMethod(
    String name,
    int access,
    List<TypeParameter> typeParameters,
    List<JType> parameters,
    JType returnType,
    List<JType> thrown,
    List<String> annotations,
    List<List<String>> parameterAnnotations,
    List<String> compatibleWith,
    Map<String, List<String>> annotationValues) {

  public static final String CONSTRUCTOR = "<init>";

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a field is missing
   */
  public JvmMethod {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(returnType, "returnType");
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
    thrown = List.copyOf(thrown);
    annotations = List.copyOf(annotations);
    parameterAnnotations = List.copyOf(parameterAnnotations);
    compatibleWith = List.copyOf(compatibleWith);
    annotationValues = Map.copyOf(annotationValues);
  }

  /** Returns whether the method is static. */
  public boolean isStatic() {
    return (access & Flags.STATIC) != 0;
  }

  /** Returns whether the last parameter is variadic. */
  public boolean isVarargs() {
    return (access & Flags.VARARGS) != 0;
  }

  /** Returns whether the method is a constructor. */
  public boolean isConstructor() {
    return CONSTRUCTOR.equals(name);
  }
}
