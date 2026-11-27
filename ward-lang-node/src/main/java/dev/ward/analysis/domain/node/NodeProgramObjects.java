package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads the properties of an object literal that a value can be. */
final class NodeProgramObjects {
  private static final String PROPERTY = "propertyAssignment";
  private static final String NAME = "propertyName";
  private static final String METHOD = "generatorMethod";
  private static final int KEYED_PARTS = 3;
  private static final int VALUE = 2;

  private NodeProgramObjects() {}

  /**
   * The expression that an object literal gives a property.
   *
   * @param object the value, which must be an object literal with plain keys
   * @param name the property
   * @return none when the value is not such an object; an empty inner value when the object has
   *     no such property
   */
  static Optional<Optional<SyntaxNode>> property(
      final NodeProgramValues.Term object,
      final String name
  ) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(object.expression());
    if (parts.size() != 1 || !"objectLiteral".equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    SyntaxNode found = null;
    for (final SyntaxNode child : parts.get(0).children()) {
      if (!PROPERTY.equals(child.kind())) {
        if (child.children().isEmpty()) {
          continue;
        }
        return Optional.empty();
      }
      final Optional<String> key = key(child);
      if (key.isEmpty()) {
        return Optional.empty();
      }
      if (key.get().equals(name)) {
        found = child;
      }
    }
    return Optional.of(Optional.ofNullable(found).map(NodeProgramObjects::valueOf));
  }

  /** The value of every property of an object literal, or none when a key is not plain. */
  static Optional<List<SyntaxNode>> values(final NodeProgramValues.Term object) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(object.expression());
    if (parts.size() != 1 || !"objectLiteral".equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> found = new java.util.ArrayList<>();
    for (final SyntaxNode child : parts.get(0).children()) {
      if (PROPERTY.equals(child.kind())) {
        if (key(child).isEmpty()) {
          return Optional.empty();
        }
        found.add(valueOf(child));
      } else if (!child.children().isEmpty()) {
        return Optional.empty();
      }
    }
    return Optional.of(found);
  }

  /** The plain key of a property, or none for a computed key or a spread. */
  private static Optional<String> key(final SyntaxNode property) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(property);
    if (parts.isEmpty()) {
      return Optional.empty();
    }
    final SyntaxNode first = firstName(parts);
    if (first == null) {
      return Optional.empty();
    }
    if (NAME.equals(first.kind()) && NodeFlowSyntax.text(first).startsWith("[")) {
      return Optional.empty();
    }
    if (NAME.equals(first.kind()) || "identifierOrKeyWord".equals(first.kind())) {
      return Optional.of(unquoted(NodeFlowSyntax.text(first)));
    }
    return Optional.empty();
  }

  /** The name node of a property: the first part, or the name inside a method. */
  private static SyntaxNode firstName(final List<SyntaxNode> parts) {
    final SyntaxNode first = parts.get(0);
    if (METHOD.equals(first.kind())) {
      return NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(first), NAME);
    }
    return NAME.equals(first.kind()) || "identifierOrKeyWord".equals(first.kind()) ? first : null;
  }

  private static String unquoted(final String text) {
    final boolean quoted =
        text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'')
            && text.charAt(text.length() - 1) == text.charAt(0);
    return quoted ? text.substring(1, text.length() - 1) : text;
  }

  /** The value of a keyed property, or the property itself for a shorthand or a method. */
  private static SyntaxNode valueOf(final SyntaxNode property) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(property);
    final boolean keyed = parts.size() == KEYED_PARTS && ":".equals(
        NodeFlowSyntax.text(parts.get(1))
    );
    return keyed ? parts.get(VALUE) : property;
  }

  /** Whether a term is an array, an object or a function, whose parts can be changed. */
  static boolean structure(final NodeProgramValues.Term term) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(term.expression());
    return PROPERTY.equals(term.expression().kind())
        || parts.size() == 1
        && ("arrayLiteral".equals(parts.get(0).kind())
            || "objectLiteral".equals(parts.get(0).kind())
            || "anonymousFunction".equals(parts.get(0).kind()));
  }
}
