package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles the loops, variable declarations and catch clauses of a script. */
final class NodeScopeStatements {

  private static final Set<ScopeKind> HOIST_TARGETS =
      Set.of(ScopeKind.FUNCTION, ScopeKind.LAMBDA, ScopeKind.MODULE);
  private static final String SINGLE = "singleExpression";
  private static final String IDENTIFIER = "identifier";

  private static final String CONST = "const";

  private final NodeScopeWalk walk;

  NodeScopeStatements(final NodeScopeWalk walk) {
    this.walk = walk;
  }

  /**
   * A loop has a scope for its header; {@code for (x of y)} without a declaration writes {@code x}.
   */
  void loop(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    for (int index = 0; index < parts.size(); index++) {
      final String token = NodeFlowSyntax.text(parts.get(index));
      final boolean iterates = "in".equals(token) || "of".equals(token);
      if (iterates && index > 0 && SINGLE.equals(parts.get(index - 1).kind())) {
        walk.names.target(parts.get(index - 1), false);
      } else if (!(index + 1 < parts.size()
          && isIterator(parts.get(index + 1))
          && SINGLE.equals(parts.get(index).kind()))) {
        walk.visit(parts.get(index));
      }
    }
    walk.builder.close();
  }

  static boolean isIterator(final SyntaxNode part) {
    return "in".equals(NodeFlowSyntax.text(part)) || "of".equals(NodeFlowSyntax.text(part));
  }

  void withStatement(final SyntaxNode node) {
    walk.builder.markOpen(walk.builder.current());
    walk.generic(node);
  }

  void catchClause(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    for (final SyntaxNode part : NodeFlowSyntax.parts(node)) {
      if ("assignable".equals(part.kind()) || IDENTIFIER.equals(part.kind())) {
        walk.names.declare(part, walk.builder.current(), BindingKind.PARAMETER, Set.of(), false);
      } else {
        walk.visit(part);
      }
    }
    walk.builder.close();
  }

  void variableStatement(final SyntaxNode node) {
    final SyntaxNode modifier = modifierOf(node);
    if (modifier == null) {
      walk.expressions.undeclared(node);
      return;
    }
    final String keyword = NodeFlowSyntax.text(modifier);
    for (final SyntaxNode declaration : declarations(node)) {
      declareVariable(declaration, keyword, false);
    }
  }

  void loopDeclaration(final SyntaxNode node) {
    final SyntaxNode modifier = modifierOf(node);
    final String keyword = modifier == null ? "let" : NodeFlowSyntax.text(modifier);
    NodeFlowSyntax.allOf(NodeFlowSyntax.parts(node), "variableDeclaration")
        .forEach(declaration -> declareVariable(declaration, keyword, true));
  }

  /** The {@code varModifier} of a statement or of its declaration list, or null. */
  static SyntaxNode modifierOf(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode own = NodeFlowSyntax.firstOf(parts, "varModifier");
    if (own != null) {
      return own;
    }
    final SyntaxNode list = NodeFlowSyntax.firstOf(parts, "variableDeclarationList");
    return list == null ? null : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(list), "varModifier");
  }

  static List<SyntaxNode> declarations(final SyntaxNode statement) {
    final SyntaxNode list =
        "variableDeclarationList".equals(statement.kind())
            ? statement
            : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(statement), "variableDeclarationList");
    return list == null
        ? List.of()
        : NodeFlowSyntax.allOf(NodeFlowSyntax.parts(list), "variableDeclaration");
  }

  /** Declares the target of one declarator under the keyword's rules, then visits the rest. */
  void declareVariable(final SyntaxNode declaration, final String keyword, final boolean loop) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(declaration);
    final boolean isVar = "var".equals(keyword);
    final int scope = isVar ? walk.builder.nearest(HOIST_TARGETS) : walk.builder.current();
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    flags.add(isVar ? BindingFlag.HOISTED : BindingFlag.LEXICAL);
    if (CONST.equals(keyword)) {
      flags.add(BindingFlag.CONST);
    }
    if (walk.exporting && scope == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    parts.stream().skip(1).forEach(walk::visit);
    walk.names.declare(
        parts.get(0),
        scope,
        CONST.equals(keyword) ? BindingKind.CONSTANT : BindingKind.VARIABLE,
        flags,
        loop);
  }
}
