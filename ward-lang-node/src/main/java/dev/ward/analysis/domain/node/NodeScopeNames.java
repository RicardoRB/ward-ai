package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Declares the names of binding patterns and records the names of assignment targets. A pattern is
 * an identifier, or an array or object literal of patterns; the grammars share these shapes.
 */
final class NodeScopeNames {

  private static final int TRIPLE = 3;

  private static final Pattern WORD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Set<String> WRAPPERS =
      Set.of(
          "assignable",
          "identifierOrPattern",
          "identifierName",
          "identifierOrKeyWord",
          "identifier",
          "singleExpression",
          "arrayElement",
          "expressionSequence",
          "bindingPattern",
          "moduleExportName");
  private static final Set<String> RESERVED = Set.of("this", "super", "null", "true", "false");
  private static final Set<String> TYPE_NODES =
      Set.of("typeAnnotation", "typeParameters", "typeArguments");
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String COLON = ":";
  private static final String EQUALS = "=";

  private final ScopeBuilder builder;
  private final Consumer<SyntaxNode> expression;

  NodeScopeNames(final ScopeBuilder builder, final Consumer<SyntaxNode> expression) {
    this.builder = builder;
    this.expression = expression;
  }

  /** Descends through the nodes that wrap one operand. */
  static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    List<SyntaxNode> parts = NodeFlowSyntax.parts(current);
    while (WRAPPERS.contains(current.kind()) && parts.size() == 1) {
      current = parts.get(0);
      parts = NodeFlowSyntax.parts(current);
    }
    return current;
  }

  /** Reports whether {@code node} is a single name token. */
  static boolean isName(final SyntaxNode node) {
    final String text = NodeFlowSyntax.text(node);
    return node.children().isEmpty() && WORD.matcher(text).matches() && !RESERVED.contains(text);
  }

  /** The name token a node is made of alone, or null. */
  static SyntaxNode nameOf(final SyntaxNode node) {
    final SyntaxNode inner = unwrap(node);
    return isName(inner) ? inner : null;
  }

  /**
   * Declares the names {@code pattern} binds in scope {@code scope}, each with a write; the
   * defaults and computed keys inside the pattern are visited as expressions.
   */
  void declare(
      final SyntaxNode pattern,
      final int scope,
      final BindingKind kind,
      final Set<BindingFlag> flags,
      final boolean destructured) {
    final SyntaxNode inner = unwrap(pattern);
    if (TYPE_NODES.contains(inner.kind())) {
      return;
    }
    if (isName(inner)) {
      declareName(inner, scope, kind, flags, destructured);
    } else if (isAssignmentWithDefault(inner)) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(inner);
      declare(parts.get(0), scope, kind, flags, destructured);
      expression.accept(parts.get(2));
    } else {
      declareParts(inner, scope, kind, flags, destructured);
    }
  }

  private void declareParts(
      final SyntaxNode inner,
      final int scope,
      final BindingKind kind,
      final Set<BindingFlag> flags,
      final boolean destructured) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(inner);
    if (PROPERTY_ASSIGNMENT.equals(inner.kind()) && hasToken(inner, COLON)) {
      expression.accept(parts.get(0));
      declare(parts.get(parts.size() - 1), scope, kind, flags, true);
    } else {
      for (final SyntaxNode part : parts) {
        declare(part, scope, kind, flags, destructured || isContainer(inner));
      }
    }
  }

  private static boolean isContainer(final SyntaxNode node) {
    return "arrayLiteral".equals(node.kind())
        || "objectLiteral".equals(node.kind())
        || "elementList".equals(node.kind());
  }

  private static boolean isAssignmentWithDefault(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    return "singleExpression".equals(node.kind())
        && parts.size() == TRIPLE
        && EQUALS.equals(NodeFlowSyntax.text(parts.get(1)));
  }

  /** A shorthand property {@code { id }}: a property whose only part is the name it binds. */
  static boolean isShorthand(final SyntaxNode node) {
    return PROPERTY_ASSIGNMENT.equals(node.kind()) && NodeFlowSyntax.parts(node).size() == 1;
  }

  private static boolean hasToken(final SyntaxNode node, final String token) {
    return NodeFlowSyntax.parts(node).stream()
        .anyMatch(part -> token.equals(NodeFlowSyntax.text(part)));
  }

  private void declareName(
      final SyntaxNode name,
      final int scope,
      final BindingKind kind,
      final Set<BindingFlag> flags,
      final boolean destructured) {
    final Set<BindingFlag> all = EnumSet.noneOf(BindingFlag.class);
    all.addAll(flags);
    if (destructured) {
      all.add(BindingFlag.DESTRUCTURED);
    }
    final String text = NodeFlowSyntax.text(name);
    builder.declareIn(scope, text, kind, name.span(), all);
    builder.referenceIn(scope, text, name.span(), ReferenceAccess.WRITE);
  }

  /** Records the names an assignment target writes; {@code compound} also reads them. */
  void target(final SyntaxNode node, final boolean compound) {
    final SyntaxNode inner = unwrap(node);
    if (isName(inner)) {
      final String text = NodeFlowSyntax.text(inner);
      if (compound) {
        builder.reference(text, inner.span(), ReferenceAccess.READ);
      }
      builder.reference(text, inner.span(), ReferenceAccess.WRITE);
    } else if (isContainer(inner)) {
      NodeFlowSyntax.parts(inner).forEach(part -> target(part, compound));
    } else if (PROPERTY_ASSIGNMENT.equals(inner.kind()) && hasToken(inner, COLON)) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(inner);
      expression.accept(parts.get(0));
      target(parts.get(parts.size() - 1), compound);
    } else if (isShorthand(inner)) {
      target(NodeFlowSyntax.parts(inner).get(0), compound);
    } else if (isAssignmentWithDefault(inner)) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(inner);
      target(parts.get(0), compound);
      expression.accept(parts.get(2));
    } else if (!inner.children().isEmpty()) {
      expression.accept(inner);
    }
  }
}
