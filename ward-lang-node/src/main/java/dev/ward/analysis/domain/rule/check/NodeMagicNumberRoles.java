package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.firstLeaf;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.isAssign;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.tokenBefore;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the expression that a number literal forms in the tree, and the role it plays there,
 * following the ESTree parent that ESLint's no-magic-numbers looks at: a property value, a default
 * value, a class field, a declarator, an array index, a radix, a JSX expression, or another
 * expression.
 */
final class NodeMagicNumberRoles {

  private static final String LITERAL_EXPRESSION = "singleExpression";
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String INITIALIZER = "initializer";
  private static final String DECLARATION = "variableDeclaration";
  private static final String STATEMENT = "variableStatement";
  private static final String MINUS = "-";
  private static final String CONST = "const";

  private final Map<SyntaxNode, SyntaxNode> parents = new IdentityHashMap<>();
  private final NodeMagicNumberContext context = new NodeMagicNumberContext(parents);

  /** The role a number literal plays: the ESTree parent that ESLint's rule reads. */
  enum Role {
    PROPERTY,
    DEFAULT,
    CLASS_FIELD,
    DECLARATOR,
    MEMBER_INDEX,
    ASSIGNED_MEMBER,
    RADIX,
    JSX,
    OTHER
  }

  /**
   * The expression of a literal, with its sign when it is negated.
   *
   * @param node the expression, the negation included when the literal is negated
   * @param negative whether the literal is negated
   * @param role the role of the expression
   * @param constant whether a declarator is a {@code const}
   */
  record Usage(SyntaxNode node, boolean negative, Role role, boolean constant) {}

  /** Indexes the parent of every node under {@code root}. */
  NodeMagicNumberRoles(final SyntaxNode root) {
    index(root);
  }

  private void index(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      parents.put(child, node);
      index(child);
    }
  }

  /** The usage of the number literal leaf. */
  Usage usage(final SyntaxNode leaf) {
    SyntaxNode node = ancestor(leaf, LITERAL_EXPRESSION).orElse(leaf);
    boolean negative = false;
    while (negated(node).isPresent()) {
      node = negated(node).get();
      negative = true;
    }
    final SyntaxNode parent = parents.get(node);
    if (parent == null) {
      return new Usage(node, negative, Role.OTHER, false);
    }
    return new Usage(node, negative, role(node, parent), constant(parent));
  }

  /** The unary minus that negates the expression, when the expression is its operand. */
  private Optional<SyntaxNode> negated(final SyntaxNode node) {
    final SyntaxNode parent = parents.get(node);
    if (parent == null || !LITERAL_EXPRESSION.equals(parent.kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> parts = parent.children();
    final boolean minus =
        parts.size() == 2
            && NodeMagicNumberSyntax.isToken(parts.get(0), MINUS)
            && parts.get(1).equals(node);
    return minus ? Optional.of(parent) : Optional.empty();
  }

  private Role role(final SyntaxNode node, final SyntaxNode parent) {
    return switch (parent.kind()) {
      case PROPERTY_NAME -> Role.PROPERTY;
      case PROPERTY_ASSIGNMENT -> propertyRole(node, parent);
      case INITIALIZER -> context.initializerRole(parent);
      case DECLARATION -> Role.DECLARATOR;
      case LITERAL_EXPRESSION -> context.assignmentRole(node, parent);
      default -> context.wrapperRole(node, parent);
    };
  }

  /** A property value after {@code :}, or a destructuring default after {@code =}. */
  private static Role propertyRole(final SyntaxNode node, final SyntaxNode property) {
    return isAssign(tokenBefore(property, node)) ? Role.DEFAULT : Role.PROPERTY;
  }

  /** Whether the declaration is a {@code const} one, from the keyword of its statement. */
  private boolean constant(final SyntaxNode declaration) {
    return DECLARATION.equals(declaration.kind())
        && ancestor(declaration, STATEMENT)
            .map(statement -> CONST.equals(firstLeaf(statement)))
            .orElse(false);
  }

  private Optional<SyntaxNode> ancestor(final SyntaxNode node, final String kind) {
    SyntaxNode current = parents.get(node);
    while (current != null && !kind.equals(current.kind())) {
      current = parents.get(current);
    }
    return Optional.ofNullable(current);
  }
}
