package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code try} whose body has more statements than the limit, like Pylint's {@code
 * too-many-try-statements}. The handlers, the {@code else} and the {@code finally} are not counted.
 */
public final class TryBodyLengthCheck implements RuleCheck {

  private static final Set<String> TRIES = Set.of("try_stmt");
  private static final Set<String> STATEMENTS = Set.of("simple_stmt", "compound_stmt");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the most statements a try body may have
   * @param message the message of each finding; {@code {value}} and {@code {max}} are filled in
   */
  public TryBodyLengthCheck(final int max, final String message) {
    this.max = max;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TRIES)) {
      final int count = bodyStatements(visit.node());
      if (count > max) {
        found.add(
            new RuleMatch(
                visit.node().span(),
                message
                    .replace("{value}", String.valueOf(count))
                    .replace("{max}", String.valueOf(max))));
      }
    }
    return List.copyOf(found);
  }

  private static int bodyStatements(final SyntaxNode tryStatement) {
    return StatementShapes.blocks(tryStatement).stream()
        .findFirst()
        .map(body -> SyntaxWalk.visits(body, STATEMENTS).size())
        .orElse(0);
  }
}
