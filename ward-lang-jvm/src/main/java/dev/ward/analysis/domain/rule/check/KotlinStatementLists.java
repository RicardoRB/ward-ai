package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads the statements of a Kotlin {@code statements} node. */
final class KotlinStatementLists {

  static final String STATEMENTS = "statements";
  private static final int SINGLE_CHILD = 1;
  private static final String STATEMENT = "statement";

  private KotlinStatementLists() {}

  /** The statement children of {@code list}, without the separators. */
  static List<SyntaxNode> statements(final SyntaxNode list) {
    return list.children().stream().filter(child -> STATEMENT.equals(child.kind())).toList();
  }

  /**
   * Follows the single-child chain below {@code statement} to the first node of {@code kind}, or
   * stops at the first node with more than one child.
   */
  static Optional<SyntaxNode> expression(final SyntaxNode statement, final String kind) {
    SyntaxNode current = statement;
    while (!kind.equals(current.kind())) {
      if (current.children().size() != SINGLE_CHILD) {
        return Optional.empty();
      }
      current = current.children().get(0);
    }
    return Optional.of(current);
  }
}
