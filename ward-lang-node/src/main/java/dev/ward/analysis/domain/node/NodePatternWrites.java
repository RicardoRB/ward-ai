package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Set;

/**
 * Writes the names that a destructuring assignment binds, after its value was read. A default or a
 * computed key is read where the pattern reaches it; a member target inside the pattern is read.
 */
final class NodePatternWrites {

  private static final String EQUALS = "=";
  private static final String COLON = ":";
  private static final String SINGLE = "singleExpression";
  private static final String BINDING_PATTERN = "bindingPattern";
  private static final String OBJECT_LITERAL = "objectLiteral";
  private static final String ARRAY_LITERAL = "arrayLiteral";
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String PROPERTY_NAME = "propertyName";
  private static final int TRIPLE = 3;
  private static final Set<String> PATTERN_KINDS =
      Set.of(OBJECT_LITERAL, ARRAY_LITERAL, "elementList", "arrayElement");

  private final NodeScopes scopes;
  private final NodeWalker walker;

  NodePatternWrites(final NodeScopes scopes, final NodeWalker walker) {
    this.scopes = scopes;
    this.walker = walker;
  }

  /** The object or array literal a destructuring target is made of, or null for any other node. */
  static SyntaxNode literalOf(final SyntaxNode node) {
    SyntaxNode target = node;
    while ((SINGLE.equals(target.kind()) || BINDING_PATTERN.equals(target.kind()))
        && NodeFlowSyntax.parts(target).size() == 1) {
      target = NodeFlowSyntax.parts(target).get(0);
    }
    return OBJECT_LITERAL.equals(target.kind()) || ARRAY_LITERAL.equals(target.kind())
        ? target
        : null;
  }

  /** Writes the names of a pattern, whose value the caller has already read. */
  void assign(final SyntaxNode pattern, final List<VariableAccess> found) {
    destructure(pattern, found);
  }

  private void destructure(final SyntaxNode node, final List<VariableAccess> found) {
    if (NodeSyntax.isName(node.kind())) {
      write(NodeSyntax.leafOf(node), found);
      return;
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (PROPERTY_ASSIGNMENT.equals(node.kind())) {
      property(parts, found);
    } else if (SINGLE.equals(node.kind()) && parts.size() == TRIPLE && isDefault(parts)) {
      walker.walk(parts.get(2), found);
      destructure(parts.get(0), found);
    } else if (PATTERN_KINDS.contains(node.kind())
        || (SINGLE.equals(node.kind()) && parts.size() == 1)) {
      node.children().forEach(child -> destructure(child, found));
    } else {
      walker.walk(node, found);
    }
  }

  /** A property of an object pattern: a keyed value, a name with a default, or a shorthand name. */
  private void property(final List<SyntaxNode> parts, final List<VariableAccess> found) {
    if (NodeSyntax.hasToken(parts, COLON)) {
      final SyntaxNode key = parts.get(0);
      if (!NodeSyntax.isName(key.kind()) && !PROPERTY_NAME.equals(key.kind())) {
        walker.walk(key, found);
      }
      destructure(parts.get(parts.size() - 1), found);
    } else if (NodeSyntax.hasToken(parts, EQUALS)) {
      walker.walk(parts.get(2), found);
      write(NodeSyntax.leafOf(parts.get(0)), found);
    } else {
      parts.forEach(part -> destructure(part, found));
    }
  }

  private static boolean isDefault(final List<SyntaxNode> parts) {
    return EQUALS.equals(NodeFlowSyntax.text(parts.get(1)));
  }

  private void write(final SyntaxNode name, final List<VariableAccess> found) {
    if (name != null) {
      scopes.access(AccessKind.WRITE, name, name.span(), found);
    }
  }
}
