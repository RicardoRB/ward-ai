package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports literals (the filter's kinds) whose text is not in {@code ignore} and that have no
 * ancestor in {@code ignoreContexts}. With a repeat limit, a literal is reported only from its
 * occurrence beyond that limit. The user options are {@code ignore}, {@code ignore-contexts} and
 * {@code max-repeats}.
 */
public final class LiteralCheck implements UnitCheck {

  /** The repeat limit that turns repeat counting off. */
  public static final int NO_REPEAT_LIMIT = -1;

  private final NodeFilter filter;
  private final Set<String> ignore;
  private final Set<String> ignoreContexts;
  private final int maxRepeats;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the literal kinds
   * @param ignore literal texts that are allowed
   * @param ignoreContexts ancestor kinds inside which a literal is allowed
   * @param maxRepeats how often the same literal may occur, or {@link #NO_REPEAT_LIMIT}
   * @param message the message; {@code {value}} is the literal's text
   */
  public LiteralCheck(
      final NodeFilter filter,
      final Set<String> ignore,
      final Set<String> ignoreContexts,
      final int maxRepeats,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.ignore = Set.copyOf(ignore);
    this.ignoreContexts = Set.copyOf(ignoreContexts);
    this.maxRepeats = maxRepeats;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final Map<String, Integer> seen = new HashMap<>();
    return filter.select(root, file).stream()
        .filter(this::notAllowed)
        .filter(visit -> beyondLimit(seen, SyntaxWalk.text(visit.node())))
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(message, SyntaxWalk.text(visit.node()), "")))
        .toList();
  }

  private boolean notAllowed(final SyntaxWalk.Visit visit) {
    return !ignore.contains(SyntaxWalk.text(visit.node()))
        && visit.ancestors().stream().noneMatch(a -> ignoreContexts.contains(a.kind()));
  }

  private boolean beyondLimit(final Map<String, Integer> seen, final String text) {
    final int occurrence = seen.merge(text, 1, Integer::sum);
    return maxRepeats == NO_REPEAT_LIMIT || occurrence > maxRepeats;
  }
}
