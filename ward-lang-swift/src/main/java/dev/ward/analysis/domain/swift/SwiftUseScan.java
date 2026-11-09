package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the identifiers a Swift unit uses and the names it assigns with a plain {@code =}. The
 * target of a plain assignment is neither a use nor a read, while a compound assignment reads its
 * target.
 */
final class SwiftUseScan {

  private static final String IMPORT = "import_declaration";
  private static final String IDENTIFIER = "Identifier";
  private static final String TEXT = "text";
  private static final String EXPRESSION = "expression";
  private static final String PREFIX = "prefix_expression";
  private static final String OPERATORS = "binary_expressions";
  private static final String ASSIGN = "=";
  private static final String INHERITANCE = "type_inheritance_clause";

  private SwiftUseScan() {}

  /**
   * Records the target of each plain assignment: its name is assigned, and its span is not a use. A
   * compound assignment reads its target, so it is left as a use.
   */
  static void collectAssignments(
      final SyntaxNode node, final Set<Span> excluded, final Set<String> assigned) {
    if (IMPORT.equals(node.kind())) {
      return;
    }
    if (EXPRESSION.equals(node.kind())) {
      assignedTarget(node)
          .ifPresent(
              target -> {
                excluded.add(target.span());
                assigned.add(SwiftSyntax.textOf(target));
              });
    }
    for (final SyntaxNode child : node.children()) {
      collectAssignments(child, excluded, assigned);
    }
  }

  /** The plain name a plain {@code =} assigns to, if the expression is one. */
  static Optional<SyntaxNode> assignedTarget(final SyntaxNode expression) {
    final SyntaxNode target = SwiftSyntax.child(expression, PREFIX).orElse(null);
    final SyntaxNode operators = SwiftSyntax.child(expression, OPERATORS).orElse(null);
    if (target == null
        || operators == null
        || !ASSIGN.equals(SwiftScopeExpressions.assignment(operators))) {
      return Optional.empty();
    }
    return Optional.ofNullable(SwiftScopeExpressions.plainName(target));
  }

  /** Records every identifier outside imports and outside the excluded spans. */
  static void collectUsed(final SyntaxNode node, final Set<Span> excluded, final Set<String> used) {
    collectUses(node, excluded, used, false);
  }

  /** Records the identifiers; one inside a conformance or inheritance clause is marked as such. */
  private static void collectUses(
      final SyntaxNode node,
      final Set<Span> excluded,
      final Set<String> used,
      final boolean conformance) {
    if (IMPORT.equals(node.kind())) {
      return;
    }
    final String name = node.fields().get(TEXT);
    if (IDENTIFIER.equals(node.kind()) && name != null && !excluded.contains(node.span())) {
      used.add(conformance ? SwiftFileSummarizer.CONFORMANCE_PREFIX + name : name);
    }
    final boolean inClause = conformance || INHERITANCE.equals(node.kind());
    for (final SyntaxNode child : node.children()) {
      collectUses(child, excluded, used, inClause);
    }
  }
}
