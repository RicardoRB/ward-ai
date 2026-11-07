package dev.ward.analysis.domain.types;

import java.util.List;
import java.util.Objects;

/**
 * Everything a resolver answered for one span. A {@code null} component and an empty list both mean
 * that the fact is unknown or absent; rules must not report a finding that depends on it.
 *
 * @param type the type of the expression
 * @param shape the shape of the type declared or typed here
 * @param call the invocation target
 * @param annotations the annotation names of the declaration here
 * @param scopes the annotation names of the enclosing declarations, nearest first
 * @param overrides the methods this method declaration overrides
 * @param thrown the checked exceptions thrown
 * @param constant the compile-time constant value
 */
public record SpanFacts(
    TypeRef type,
    TypeShape shape,
    CallTarget call,
    List<String> annotations,
    List<String> scopes,
    List<CallTarget> overrides,
    List<TypeRef> thrown,
    ConstantValue constant) {

  /** The span about which nothing is known. */
  public static final SpanFacts UNKNOWN =
      new SpanFacts(null, null, null, List.of(), List.of(), List.of(), List.of(), null);

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is missing
   */
  public SpanFacts {
    Objects.requireNonNull(annotations, "annotations");
    annotations = List.copyOf(annotations);
    scopes = List.copyOf(scopes);
    overrides = List.copyOf(overrides);
    thrown = List.copyOf(thrown);
  }
}
