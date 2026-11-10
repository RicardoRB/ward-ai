package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an {@code except} clause whose exception type an earlier clause of the same {@code try}
 * already names, like pyflakes-style {@code B025}. The clause's {@code as} alias is not part of the
 * type.
 */
public final class DuplicateHandlerCheck implements RuleCheck {

  private static final Set<String> TRIES = Set.of("try_stmt");
  private static final String HANDLER = "except_clause";
  private static final String EXCEPT = "except";
  private static final String ALIAS = "as";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public DuplicateHandlerCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TRIES)) {
      found.addAll(duplicates(visit.node()));
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> duplicates(final SyntaxNode node) {
    final Set<String> seen = new HashSet<>();
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (HANDLER.equals(child.kind()) && !seen.add(exceptionType(child))) {
        found.add(new RuleMatch(child.span(), message));
      }
    }
    return found;
  }

  /** The exception type of a clause: its leaves after {@code except}, up to any {@code as}. */
  private static String exceptionType(final SyntaxNode clause) {
    final StringBuilder type = new StringBuilder();
    boolean started = false;
    for (final SyntaxNode leaf : SyntaxWalk.leaves(clause)) {
      final String text = SyntaxWalk.text(leaf);
      if (started) {
        if (ALIAS.equals(text)) {
          break;
        }
        type.append(text);
      } else {
        started = EXCEPT.equals(text);
      }
    }
    return type.toString();
  }
}
