package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles functions, arrow functions and their parameters. */
final class NodeScopeFunctions {

  private static final Set<String> DECLARATIONS =
      Set.of("functionDeclaration", "generatorFunctionDeclaration");
  private static final String SINGLE = "singleExpression";
  private static final String IDENTIFIER = "identifier";

  private final NodeScopeWalk walk;

  NodeScopeFunctions(final NodeScopeWalk walk) {
    this.walk = walk;
  }

  void functionLike(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (!hasBody(parts)) {
      walk.expressionFunction = "anonymousFunction".equals(node.kind());
      walk.generic(node);
      walk.expressionFunction = false;
      return;
    }
    final boolean declaration = DECLARATIONS.contains(node.kind()) && !walk.expressionFunction;
    walk.expressionFunction = false;
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    if (declaration && name != null) {
      declareFunctionName(name);
    }
    walk.builder.open(isArrow(node, parts) ? ScopeKind.LAMBDA : ScopeKind.FUNCTION, node.span());
    if (!declaration && name != null && DECLARATIONS.contains(node.kind())) {
      walk.builder.declare(NodeFlowSyntax.text(name), BindingKind.FUNCTION, name.span(), Set.of());
    }
    walk.generic(node);
    walk.builder.close();
  }

  static boolean hasBody(final List<SyntaxNode> parts) {
    return NodeFlowSyntax.firstOf(parts, "functionBody") != null
        || NodeFlowSyntax.firstOf(parts, "arrowFunctionBody") != null;
  }

  static boolean isArrow(final SyntaxNode node, final List<SyntaxNode> parts) {
    return "arrowFunctionDeclaration".equals(node.kind())
        || NodeFlowSyntax.firstOf(parts, "arrowFunctionParameters") != null;
  }

  void declareFunctionName(final SyntaxNode name) {
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.HOISTED);
    if (walk.exporting && walk.builder.current() == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    final String text = NodeFlowSyntax.text(name);
    walk.builder.declare(text, BindingKind.FUNCTION, name.span(), flags);
  }

  void parameter(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    SyntaxNode pattern = null;
    for (final SyntaxNode part : parts) {
      if (pattern == null && isPatternHolder(part)) {
        pattern = part;
        walk.names.declare(part, walk.builder.current(), BindingKind.PARAMETER, Set.of(), false);
      } else {
        walk.visit(part);
      }
    }
  }

  static boolean isPatternHolder(final SyntaxNode part) {
    return Set.of(
            "assignable",
            "identifierOrPattern",
            IDENTIFIER,
            "identifierName",
            SINGLE,
            "arrayLiteral",
            "objectLiteral",
            "bindingPattern")
        .contains(part.kind());
  }

  void arrowParameters(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (NodeFlowSyntax.firstOf(parts, "formalParameterList") != null) {
      walk.generic(node);
      return;
    }
    parts.stream()
        .filter(part -> !"=>".equals(NodeFlowSyntax.text(part)))
        .forEach(
            part ->
                walk.names.declare(
                    part, walk.builder.current(), BindingKind.PARAMETER, Set.of(), false));
  }
}
