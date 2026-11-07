package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the modifiers that are out of the order of JLS 8.1.1 and the annotations that follow a
 * keyword modifier. Each holder checks its own modifier children, in source order.
 */
public final class ModifierOrderCheck implements RuleCheck {

  private static final Set<String> MODIFIER_KINDS =
      Set.of("modifier", "classOrInterfaceModifier", "variableModifier", "interfaceMethodModifier");
  private static final Map<String, Integer> RANK =
      Map.ofEntries(
          Map.entry("public", 0),
          Map.entry("protected", 1),
          Map.entry("private", 2),
          Map.entry("abstract", 3),
          Map.entry("default", 4),
          Map.entry("static", 5),
          Map.entry("sealed", 6),
          Map.entry("non-sealed", 7),
          Map.entry("final", 8),
          Map.entry("transient", 9),
          Map.entry("volatile", 10),
          Map.entry("synchronized", 11),
          Map.entry("native", 12),
          Map.entry("strictfp", 13));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public ModifierOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    checkHolder(root, matches);
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      checkHolder(node, matches);
    }
    return List.copyOf(matches);
  }

  private void checkHolder(final SyntaxNode holder, final List<RuleMatch> matches) {
    int highest = -1;
    boolean keywordSeen = false;
    for (final SyntaxNode child : holder.children()) {
      if (!MODIFIER_KINDS.contains(child.kind())) {
        continue;
      }
      final String text = SyntaxWalk.text(child).trim();
      if (text.startsWith("@")) {
        if (keywordSeen) {
          matches.add(CheckSupport.at(child.span(), message));
        }
      } else if (RANK.containsKey(text)) {
        final int rank = RANK.get(text);
        if (rank < highest) {
          matches.add(CheckSupport.at(child.span(), message));
        }
        highest = Math.max(highest, rank);
        keywordSeen = true;
      }
    }
  }
}
