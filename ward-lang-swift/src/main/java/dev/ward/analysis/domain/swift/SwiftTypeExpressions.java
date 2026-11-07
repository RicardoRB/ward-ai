package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Infers the types of Swift bindings and constructor calls. A literal's type is taken only from an
 * unannotated binding, where Swift gives it the default type; any other context may change it, so
 * the literal itself stays unknown.
 */
final class SwiftTypeExpressions {

  private static final Set<String> SINGLE_CHILD_WRAPPERS =
      Set.of(
          "expression",
          "prefix_expression",
          "postfix_expression",
          "primary_expression",
          "literal_expression",
          "literal");
  private static final String CALL_ARGUMENTS = "function_call_argument_clause";
  private static final String MEMBER_NAME = "declaration_identifier";

  private final SwiftTypeIndex index;
  private final SwiftTypeRefs refs;

  SwiftTypeExpressions(final SwiftTypeIndex index, final SwiftTypeRefs refs) {
    this.index = index;
    this.refs = refs;
  }

  /**
   * Returns the type of a pattern and initializer: its annotation, else the type its initializer
   * gives it. Null when neither is known.
   */
  TypeRef bindingType(final SyntaxNode binding, final String file, final String scope) {
    final SyntaxNode pattern = SwiftNodes.child(binding, "pattern");
    final SyntaxNode annotation =
        pattern == null ? null : SwiftNodes.child(pattern, "type_annotation");
    if (annotation != null) {
      final SyntaxNode type = SwiftNodes.typeChild(annotation);
      return type == null ? null : refs.of(type, file, scope);
    }
    final SyntaxNode initializer = SwiftNodes.child(binding, "initializer");
    final SyntaxNode expression =
        initializer == null ? null : SwiftNodes.child(initializer, "expression");
    if (expression == null) {
      return null;
    }
    final TypeRef literal = literalType(expression);
    return literal != null ? literal : constructorType(expression, file, scope);
  }

  /**
   * Returns the type of a call that constructs a declared, non-generic class, struct or enum, such
   * as {@code Session()}. Null for any other expression.
   */
  TypeRef constructorType(final SyntaxNode expression, final String file, final String scope) {
    final Optional<SyntaxNode> call = unwrapToCall(expression);
    if (call.isEmpty()) {
      return null;
    }
    final Optional<String> callee = calleeName(call.get());
    if (callee.isEmpty()) {
      return null;
    }
    return index
        .resolve(file, scope, List.of(callee.get()))
        .filter(decl -> decl.kind() != TypeShape.Kind.INTERFACE && !decl.isGeneric())
        .map(decl -> new TypeRef(index.refName(decl), List.of(), 0, false))
        .orElse(null);
  }

  private static TypeRef literalType(final SyntaxNode expression) {
    SyntaxNode node = expression;
    while (SINGLE_CHILD_WRAPPERS.contains(node.kind()) && node.children().size() == 1) {
      node = node.children().get(0);
    }
    return switch (node.kind()) {
      case "string_literal" -> new TypeRef("String", List.of(), 0, false);
      case "boolean_literal" -> new TypeRef("Bool", List.of(), 0, false);
      case "numeric_literal" -> numericType(node);
      default -> null;
    };
  }

  private static TypeRef numericType(final SyntaxNode numeric) {
    if (SwiftNodes.hasChild(numeric, "integer_literal")) {
      return new TypeRef("Int", List.of(), 0, false);
    }
    return SwiftNodes.hasChild(numeric, "Floating_point_literal")
        ? new TypeRef("Double", List.of(), 0, false)
        : null;
  }

  /** Descends single-child wrappers down to the call node that takes an argument list. */
  private static Optional<SyntaxNode> unwrapToCall(final SyntaxNode expression) {
    SyntaxNode node = expression;
    while (("expression".equals(node.kind()) || "prefix_expression".equals(node.kind()))
        && node.children().size() == 1) {
      node = node.children().get(0);
    }
    final boolean call =
        "postfix_expression".equals(node.kind())
            && node.children().size() == 2
            && SwiftNodes.hasChild(node, CALL_ARGUMENTS);
    return call ? Optional.of(node) : Optional.empty();
  }

  /** The name of a plain identifier callee, such as {@code Session} in {@code Session()}. */
  private static Optional<String> calleeName(final SyntaxNode call) {
    final SyntaxNode callee = call.children().get(0);
    if (!"postfix_expression".equals(callee.kind()) || callee.children().size() != 1) {
      return Optional.empty();
    }
    final SyntaxNode primary = callee.children().get(0);
    if (!"primary_expression".equals(primary.kind()) || primary.children().size() != 1) {
      return Optional.empty();
    }
    final SyntaxNode name = primary.children().get(0);
    return MEMBER_NAME.equals(name.kind()) && name.children().size() == 1
        ? Optional.of(SwiftScopeWalk.text(name))
        : Optional.empty();
  }
}
