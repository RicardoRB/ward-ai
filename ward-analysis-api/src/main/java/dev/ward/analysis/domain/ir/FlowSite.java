package dev.ward.analysis.domain.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One call, member read or parameter of a basic block, which a taint specification can match.
 *
 * @param kind what the site is
 * @param name the called or read name, or the parameter name
 * @param qualifier the receiver text as written, such as {@code st}; empty when unqualified
 * @param receiverType the fully qualified receiver type, when it is known from the file
 * @param annotations the annotation or decorator names of a parameter, in source order
 * @param arguments the spans of the arguments of a call, in order
 * @param span where the site is in the source
 */
public record FlowSite(
    FlowSiteKind kind,
    String name,
    String qualifier,
    Optional<String> receiverType,
    List<String> annotations,
    List<Span> arguments,
    Span span) {

  /**
   * Copies the lists and validates the fields.
   *
   * @throws NullPointerException if a field is missing or a list holds a null element
   */
  public FlowSite {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(qualifier, "qualifier");
    Objects.requireNonNull(receiverType, "receiverType");
    Objects.requireNonNull(span, "span");
    annotations = List.copyOf(Objects.requireNonNull(annotations, "annotations"));
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }
}
