package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the first Kotlin statement that follows a {@code return}, {@code throw}, {@code break} or
 * {@code continue} statement of the same block: it can never run.
 */
public final class UnreachableStatementCheck implements UnitCheck {

  private static final String JUMP = "jumpExpression";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the unreachable statement
   */
  public UnreachableStatementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of(KotlinStatementLists.STATEMENTS))) {
      final List<SyntaxNode> statements = KotlinStatementLists.statements(visit.node());
      for (int index = 0; index < statements.size() - 1; index++) {
        if (KotlinStatementLists.expression(statements.get(index), JUMP).isPresent()) {
          matches.add(CheckSupport.at(statements.get(index + 1).span(), message));
          break;
        }
      }
    }
    return List.copyOf(matches);
  }
}
