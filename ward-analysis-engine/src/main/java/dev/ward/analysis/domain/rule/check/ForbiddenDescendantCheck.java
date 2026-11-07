package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports, below each node of the filter's kinds, every descendant of the given kinds whose text
 * matches the pattern, if any. The search does not enter the subtrees of the {@code skip} kinds, so
 * a {@code return} in a nested function does not count for the function around it.
 */
public final class ForbiddenDescendantCheck implements UnitCheck {

  private final NodeFilter filter;
  private final Set<String> descendants;
  private final Set<String> skip;
  private final Optional<Pattern> text;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the nodes whose descendants are searched
   * @param descendants the kinds to report
   * @param skip the kinds whose subtrees are not searched
   * @param text a pattern that the descendant's text must contain, if any
   * @param message the message; {@code {value}} is the descendant's text
   */
  public ForbiddenDescendantCheck(
      final NodeFilter filter,
      final Set<String> descendants,
      final Set<String> skip,
      final Optional<Pattern> text,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.descendants = Set.copyOf(descendants);
    this.skip = Set.copyOf(skip);
    this.text = Objects.requireNonNull(text, "text");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> found = new ArrayList<>();
    filter.select(root, file).forEach(visit -> search(visit.node(), found));
    return List.copyOf(found);
  }

  private void search(final SyntaxNode parent, final List<RuleMatch> found) {
    for (final SyntaxNode child : parent.children()) {
      if (descendants.contains(child.kind()) && holds(child)) {
        found.add(
            CheckSupport.at(child.span(), CheckSupport.fill(message, SyntaxWalk.text(child), "")));
      } else if (!skip.contains(child.kind())) {
        search(child, found);
      }
    }
  }

  private boolean holds(final SyntaxNode node) {
    return text.map(pattern -> pattern.matcher(SyntaxWalk.text(node)).find()).orElse(true);
  }
}
