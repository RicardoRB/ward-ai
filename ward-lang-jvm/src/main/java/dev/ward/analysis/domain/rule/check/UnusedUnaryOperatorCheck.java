package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a Kotlin statement that is only a unary plus or minus and is followed by another
 * statement, such as the {@code + 3} that starts a new line after {@code val sum = 1 + 2}: its
 * value is unused. The last statement of a block is skipped, because a lambda returns it.
 */
public final class UnusedUnaryOperatorCheck implements UnitCheck {

  private static final String PREFIX = "prefixUnaryExpression";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the statement
   */
  public UnusedUnaryOperatorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of(KotlinStatementLists.STATEMENTS))) {
      final List<SyntaxNode> statements = KotlinStatementLists.statements(visit.node());
      statements.stream()
          .limit(Math.max(0, statements.size() - 1L))
          .filter(UnusedUnaryOperatorCheck::isSignedOperand)
          .forEach(statement -> matches.add(CheckSupport.at(statement.span(), message)));
    }
    return List.copyOf(matches);
  }

  private static boolean isSignedOperand(final SyntaxNode statement) {
    return KotlinStatementLists.expression(statement, PREFIX)
        .map(SyntaxWalk::text)
        .filter(text -> text.startsWith("+") || text.startsWith("-"))
        .filter(text -> !text.startsWith("++") && !text.startsWith("--"))
        .isPresent();
  }
}
