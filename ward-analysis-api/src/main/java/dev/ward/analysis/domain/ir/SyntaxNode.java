package dev.ward.analysis.domain.ir;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A node of a language-specific syntax tree in the shared shape.
 *
 * <p>The {@code kind} and {@code fields} carry the language detail, so no information is lost when
 * a frontend maps its tree into this shape. Fields and children are copied on construction and
 * returned as immutable views.
 *
 * @param kind the frontend's own node name, for example {@code method_declaration}
 * @param span where the node is in the source
 * @param fields string-valued properties of the node
 * @param children child nodes in source order
 */
public record SyntaxNode(
    String kind, Span span, Map<String, String> fields, List<SyntaxNode> children) {

  /**
   * Copies the fields and children.
   *
   * @throws NullPointerException if a required value or a field key or value is missing
   */
  public SyntaxNode {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(span, "span");
    fields = Map.copyOf(fields);
    children = List.copyOf(children);
  }
}
