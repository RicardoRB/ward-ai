package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an {@code except} clause whose body text matches a pattern, like Ruff {@code S112} (a
 * body that is only {@code continue}) and {@code SIM105} (only {@code pass}, in a {@code try} of
 * one statement and one handler).
 */
public final class HandlerBodyCheck implements RuleCheck {

  private static final Set<String> KINDS = Set.of("try_stmt");
  private static final String EXCEPT_CLAUSE = "except_clause";
  private static final int BODY_OFFSET = 2;

  private final Pattern body;
  private final boolean sole;
  private final String message;

  /**
   * Creates the check.
   *
   * @param body the pattern the handler body's text must match
   * @param sole whether the {@code try} must hold one statement and one handler, with no else or
   *     finally
   * @param message the message of each finding
   */
  public HandlerBodyCheck(final Pattern body, final boolean sole, final String message) {
    this.body = Objects.requireNonNull(body, "body");
    this.sole = sole;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, KINDS)) {
      final SyntaxNode attempt = visit.node();
      if (!sole || single(attempt)) {
        handlers(attempt, found);
      }
    }
    return List.copyOf(found);
  }

  private void handlers(final SyntaxNode attempt, final List<RuleMatch> found) {
    final List<SyntaxNode> children = attempt.children();
    for (int index = 0; index + BODY_OFFSET < children.size(); index++) {
      final SyntaxNode clause = children.get(index);
      if (EXCEPT_CLAUSE.equals(clause.kind())
          && body.matcher(SyntaxWalk.text(children.get(index + BODY_OFFSET)).strip()).matches()) {
        found.add(new RuleMatch(clause.span(), message));
      }
    }
  }

  private static boolean single(final SyntaxNode attempt) {
    final long clauses =
        attempt.children().stream().filter(c -> EXCEPT_CLAUSE.equals(c.kind())).count();
    return clauses == 1
        && !StatementShapes.hasKeyword(attempt, "else")
        && !StatementShapes.hasKeyword(attempt, "finally")
        && StatementShapes.statements(StatementShapes.blocks(attempt).get(0)).size() == 1;
  }
}
