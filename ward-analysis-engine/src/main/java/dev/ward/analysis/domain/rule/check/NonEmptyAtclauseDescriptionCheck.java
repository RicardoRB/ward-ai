package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each block tag of the listed kinds that has no description. A {@code @param}, {@code
 * @throws} or {@code @exception} tag names its argument first, so the name alone is no description.
 */
public final class NonEmptyAtclauseDescriptionCheck implements RuleCheck {

  private static final Set<String> NAMED = Set.of("@param", "@throws", "@exception");

  private final Set<String> tags;
  private final String message;

  /**
   * Creates the check.
   *
   * @param tags the block tags that need a description
   * @param message the message; {@code {value}} is the tag
   */
  public NonEmptyAtclauseDescriptionCheck(final Set<String> tags, final String message) {
    this.tags = Set.copyOf(tags);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (JavadocTags.isJavadoc(node)) {
        for (final JavadocTags.Tag tag : JavadocTags.tags(node)) {
          if (tags.contains(tag.name()) && !described(tag)) {
            matches.add(CheckSupport.at(tag.span(), CheckSupport.fill(message, tag.name(), "")));
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean described(final JavadocTags.Tag tag) {
    if (!tag.continuation().isEmpty()) {
      return true;
    }
    final String description = tag.description();
    if (!NAMED.contains(tag.name())) {
      return !description.isEmpty();
    }
    final int space = description.indexOf(' ');
    return space >= 0 && !description.substring(space).isBlank();
  }
}
