package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.NodeMagicNumberTokens.firstLeaf;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberTokens.isToken;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberTokens.tokenBefore;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the expression that a number literal forms in the tree, and the role it plays there,
 * following the ESTree parent that ESLint's no-magic-numbers looks at: a property value, a default
 * value, a class field, a declarator, an array index, or another expression.
 */
final class NodeMagicNumberRoles {

  private static final String LITERAL_EXPRESSION = "singleExpression";
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String INITIALIZER = "initializer";
  private static final String PARAMETER = "optionalParameter";
  private static final String PARAMETER_ARGUMENT = "formalParameterArg";
  private static final String CLASS_FIELD = "propertyMemberDeclaration";
  private static final String FIELD = "fieldDefinition";
  private static final String DECLARATION = "variableDeclaration";
  private static final String STATEMENT = "variableStatement";
  private static final String SEQUENCE = "expressionSequence";
  private static final String ASSIGN = "=";
  private static final String MINUS = "-";
  private static final String OPEN_BRACKET = "[";
  private static final String CONST = "const";

  private final Map<SyntaxNode, SyntaxNode> parents = new IdentityHashMap<>();

  /** The role a number literal plays: the ESTree parent that ESLint's rule reads. */
  enum Role {
    PROPERTY,
    DEFAULT,
    CLASS_FIELD,
    DECLARATOR,
    MEMBER_INDEX,
    ASSIGNED_MEMBER,
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
        parts.size() == 2 && isToken(parts.get(0), MINUS) && parts.get(1).equals(node);
    return minus ? Optional.of(parent) : Optional.empty();
  }

  private Role role(final SyntaxNode node, final SyntaxNode parent) {
    return switch (parent.kind()) {
      case PROPERTY_NAME -> Role.PROPERTY;
      case PROPERTY_ASSIGNMENT -> propertyRole(node, parent);
      case INITIALIZER -> initializerRole(parent);
      case PARAMETER_ARGUMENT ->
          ASSIGN.equals(tokenBefore(parent, node)) ? Role.DEFAULT : Role.OTHER;
      case DECLARATION -> Role.DECLARATOR;
      case LITERAL_EXPRESSION -> assignmentRole(node, parent);
      case SEQUENCE -> indexRole(parent);
      default -> Role.OTHER;
    };
  }

  /** A property value after {@code :}, or a destructuring default after {@code =}. */
  private static Role propertyRole(final SyntaxNode node, final SyntaxNode property) {
    return ASSIGN.equals(tokenBefore(property, node)) ? Role.DEFAULT : Role.PROPERTY;
  }

  private Role initializerRole(final SyntaxNode initializer) {
    final SyntaxNode owner = parents.get(initializer);
    if (owner == null) {
      return Role.OTHER;
    }
    if (PARAMETER.equals(owner.kind())) {
      return Role.DEFAULT;
    }
    return CLASS_FIELD.equals(owner.kind()) || FIELD.equals(owner.kind())
        ? Role.CLASS_FIELD
        : Role.OTHER;
  }

  /** The right side of an assignment; a target of a member is the object form. */
  private Role assignmentRole(final SyntaxNode node, final SyntaxNode expression) {
    if (!ASSIGN.equals(tokenBefore(expression, node))) {
      return Role.OTHER;
    }
    final SyntaxNode owner = parents.get(expression);
    if (owner != null && PROPERTY_ASSIGNMENT.equals(owner.kind())) {
      return Role.DEFAULT;
    }
    final SyntaxNode target = expression.children().get(0);
    return NodeSyntax.leaves(target).size() == 1 ? Role.OTHER : Role.ASSIGNED_MEMBER;
  }

  /** The index of a member access, which the sequence of the bracket holds. */
  private Role indexRole(final SyntaxNode sequence) {
    final SyntaxNode access = parents.get(sequence);
    if (access == null || !LITERAL_EXPRESSION.equals(access.kind())) {
      return Role.OTHER;
    }
    return access.children().stream().anyMatch(part -> isToken(part, OPEN_BRACKET))
        ? Role.MEMBER_INDEX
        : Role.OTHER;
  }

  private boolean constant(final SyntaxNode parent) {
    return DECLARATION.equals(parent.kind())
        && ancestor(parent, STATEMENT)
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
