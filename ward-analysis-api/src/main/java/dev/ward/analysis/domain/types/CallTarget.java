package dev.ward.analysis.domain.types;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The method or constructor an invocation resolves to.
 *
 * @param declaringType the type that declares it
 * @param name the method name, {@code <init>} for a constructor
 * @param parameters the declared parameter types, erased; empty if one did not resolve
 * @param returnType the declared return type, or {@code null} when it did not resolve
 * @param isStatic whether it is static
 * @param isVarargs whether the last parameter is variadic
 * @param annotations the qualified names of the annotations of the method; empty when none or
 *     unknown
 * @param parameterAnnotations the qualified names of the annotations of each parameter, in order;
 *     empty when none or unknown
 * @param declaringAnnotations the qualified names of the annotations of the declaring type
 * @param compatibleWith the {@code @CompatibleWith} value of each parameter, in order: the name of
 *     the type variable the argument must be compatible with, or empty text for none
 * @param typeParameters the names of the type parameters of the method
 * @param declaringTypeParameters the names of the type parameters of the declaring type
 * @param isFinal whether the method is declared final, or {@code null} when that is not known
 * @param annotationValues the element values of the annotations of the method, by {@code
 *     <annotation>#<element>}: a string as written, a class as its qualified name, an enum constant
 *     as its name, any other constant as its text, an array as its elements; empty when none or
 *     unknown
 * @param declaringAnnotationValues the same for the annotations of the declaring type
 */
public record CallTarget(
    TypeRef declaringType,
    String name,
    List<TypeRef> parameters,
    TypeRef returnType,
    boolean isStatic,
    boolean isVarargs,
    List<String> annotations,
    List<List<String>> parameterAnnotations,
    List<String> declaringAnnotations,
    List<String> compatibleWith,
    List<String> typeParameters,
    List<String> declaringTypeParameters,
    Boolean isFinal,
    Map<String, List<String>> annotationValues,
    Map<String, List<String>> declaringAnnotationValues) {

  /**
   * Copies the parameters, annotations and annotation element values.
   *
   * @throws NullPointerException if the declaring type, name or parameters are missing
   */
  public CallTarget {
    Objects.requireNonNull(declaringType, "declaringType");
    Objects.requireNonNull(name, "name");
    parameters = List.copyOf(parameters);
    annotations = List.copyOf(annotations);
    parameterAnnotations = List.copyOf(parameterAnnotations.stream().map(List::copyOf).toList());
    declaringAnnotations = List.copyOf(declaringAnnotations);
    compatibleWith = List.copyOf(compatibleWith);
    typeParameters = List.copyOf(typeParameters);
    declaringTypeParameters = List.copyOf(declaringTypeParameters);
    annotationValues = Map.copyOf(copy(annotationValues));
    declaringAnnotationValues = Map.copyOf(copy(declaringAnnotationValues));
  }

  private static Map<String, List<String>> copy(final Map<String, List<String>> values) {
    final Map<String, List<String>> copy = new LinkedHashMap<>();
    values.forEach((key, list) -> copy.put(key, List.copyOf(list)));
    return copy;
  }
}
