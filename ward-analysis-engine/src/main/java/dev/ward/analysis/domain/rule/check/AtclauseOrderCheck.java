package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each block tag that comes before the block tag above it in the tag order, in the Javadoc
 * of a declaration of a target kind. Tags that are not in the order are ignored.
 */
public final class AtclauseOrderCheck implements RuleCheck {

  private final Set<String> kinds;
  private final Set<String> modifierKinds;
  private final List<String> tagOrder;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the kinds of the declarations whose Javadoc is checked
   * @param modifierKinds the kinds whose tokens count as modifiers and annotations
   * @param tagOrder the tags in the order they must appear
   * @param message the message; {@code {value}} is the out-of-order tag
   */
  public AtclauseOrderCheck(
      final Set<String> kinds,
      final Set<String> modifierKinds,
      final List<String> tagOrder,
      final String message) {
    this.kinds = Set.copyOf(kinds);
    this.modifierKinds = Set.copyOf(modifierKinds);
    this.tagOrder = List.copyOf(tagOrder);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      final Optional<SyntaxNode> comment =
          Declaration.of(visit, modifierKinds).previousSibling().filter(JavadocTags::isJavadoc);
      comment.ifPresent(javadoc -> matches.addAll(outOfOrder(javadoc)));
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> outOfOrder(final SyntaxNode javadoc) {
    final List<RuleMatch> matches = new ArrayList<>();
    int previous = -1;
    for (final JavadocTags.Tag tag : JavadocTags.tags(javadoc)) {
      final int index = tagOrder.indexOf(tag.name());
      if (index >= 0) {
        if (index < previous) {
          matches.add(CheckSupport.at(tag.span(), CheckSupport.fill(message, tag.name(), "")));
        }
        previous = index;
      }
    }
    return matches;
  }
}
