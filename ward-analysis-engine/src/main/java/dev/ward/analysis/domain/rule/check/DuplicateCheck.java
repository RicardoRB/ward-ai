package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports, among the children of each node of the filter's kinds, every child of an item kind whose
 * key repeats an earlier child's key. The key is the text of the child's first {@code key} node, or
 * the child's whole text when no key kind is given. A child that has no node of the key kind is not
 * compared.
 */
public final class DuplicateCheck implements UnitCheck {

  private final NodeFilter filter;
  private final Set<String> items;
  private final Optional<String> keyKind;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the parents whose children are compared
   * @param items the kinds of the children to compare
   * @param keyKind the kind of the node whose text is the key, if not the whole child
   * @param message the message; {@code {value}} is the key
   */
  public DuplicateCheck(
      final NodeFilter filter,
      final Set<String> items,
      final Optional<String> keyKind,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.items = Set.copyOf(items);
    this.keyKind = Objects.requireNonNull(keyKind, "keyKind");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    filter.select(root, file).forEach(visit -> repeats(visit.node(), matches));
    return List.copyOf(matches);
  }

  private void repeats(final SyntaxNode parent, final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    for (final SyntaxNode child : parent.children()) {
      if (items.contains(child.kind())) {
        key(child)
            .filter(key -> !seen.add(key))
            .ifPresent(
                key ->
                    matches.add(
                        CheckSupport.at(child.span(), CheckSupport.fill(message, key, ""))));
      }
    }
  }

  /** The key of {@code child}; a child without the key kind has none and is not compared. */
  private Optional<String> key(final SyntaxNode child) {
    if (keyKind.isEmpty()) {
      return Optional.of(SyntaxWalk.text(child));
    }
    return SyntaxWalk.firstDescendant(child, keyKind.get(), Set.of()).map(SyntaxWalk::text);
  }
}
