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
 * occurrence beyond that limit. The user options are {@code ignore}, {@code ignore-contexts},
 * {@code max-repeats} and {@code min-length}.
 */
public final class LiteralCheck implements UnitCheck {

  /** The repeat limit that turns repeat counting off. */
  public static final int NO_REPEAT_LIMIT = -1;

  private static final int QUOTES = 2;
  private static final String QUOTE_CHARS = "\"'`";

  private final NodeFilter filter;
  private final Set<String> ignore;
  private final Set<String> ignoreContexts;
  private final int maxRepeats;
  private final int minLength;
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
    this(filter, ignore, ignoreContexts, new Limits(maxRepeats, 0), message);
  }

  /**
   * Creates the check with a minimum length.
   *
   * @param filter the literal kinds
   * @param ignore literal texts that are allowed
   * @param ignoreContexts ancestor kinds inside which a literal is allowed
   * @param limits how often the same literal may occur and how short a literal may be
   * @param message the message; {@code {value}} is the literal's text
   */
  public LiteralCheck(
      final NodeFilter filter,
      final Set<String> ignore,
      final Set<String> ignoreContexts,
      final Limits limits,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.ignore = Set.copyOf(ignore);
    this.ignoreContexts = Set.copyOf(ignoreContexts);
    this.maxRepeats = limits.maxRepeats();
    this.minLength = limits.minLength();
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
        && contentLength(SyntaxWalk.text(visit.node())) >= minLength
        && visit.ancestors().stream().noneMatch(a -> ignoreContexts.contains(a.kind()));
  }

  /** The length of a literal without the quotes that surround a string. */
  private static int contentLength(final String text) {
    final boolean quoted =
        text.length() >= QUOTES
            && QUOTE_CHARS.indexOf(text.charAt(0)) >= 0
            && text.charAt(text.length() - 1) == text.charAt(0);
    return quoted ? text.length() - QUOTES : text.length();
  }

  private boolean beyondLimit(final Map<String, Integer> seen, final String text) {
    final int occurrence = seen.merge(text, 1, Integer::sum);
    return maxRepeats == NO_REPEAT_LIMIT || occurrence > maxRepeats;
  }

  /**
   * What the check tolerates.
   *
   * @param maxRepeats how often the same literal may occur, or {@link #NO_REPEAT_LIMIT}
   * @param minLength the fewest characters a literal needs to be reported, not counting the quotes
   *     of a string; shorter ones are allowed
   */
  public record Limits(int maxRepeats, int minLength) {}
}
