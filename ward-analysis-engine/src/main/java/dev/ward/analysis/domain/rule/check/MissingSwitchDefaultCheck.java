package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a switch statement that has no {@code default} label. A switch with a pattern label or a
 * {@code null} label is exhaustive by the compiler's check and is not reported.
 */
public final class MissingSwitchDefaultCheck implements UnitCheck {

  private static final String SWITCH = "'switch'";
  private static final String DEFAULT = "'default'";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String TYPE = "typeType";
  private static final String NULL_LABEL = "casenull";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the switch keyword
   */
  public MissingSwitchDefaultCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      final SyntaxNode node = visit.node();
      node.children().stream()
          .filter(c -> SWITCH.equals(c.kind()))
          .findFirst()
          .filter(keyword -> !hasDefaultOrExhaustiveLabel(node))
          .ifPresent(keyword -> matches.add(CheckSupport.at(keyword.span(), message)));
    }
    return List.copyOf(matches);
  }

  private static boolean hasDefaultOrExhaustiveLabel(final SyntaxNode switchNode) {
    return labels(switchNode).stream()
        .anyMatch(
            label ->
                NULL_LABEL.equals(SyntaxWalk.text(label))
                    || label.children().stream()
                        .anyMatch(c -> DEFAULT.equals(c.kind()) || TYPE.equals(c.kind())));
  }

  private static List<SyntaxNode> labels(final SyntaxNode switchNode) {
    final List<SyntaxNode> labels = new ArrayList<>();
    for (final SyntaxNode child : switchNode.children()) {
      if (GROUP.equals(child.kind())) {
        child.children().stream().filter(c -> LABEL.equals(c.kind())).forEach(labels::add);
      } else if (LABEL.equals(child.kind())) {
        labels.add(child);
      }
    }
    return labels;
  }
}
