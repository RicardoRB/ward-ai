package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code default} label that a later label of the same switch follows. The labels of a
 * switch are read in order: the labels of each case group, then the trailing labels.
 */
public final class DefaultLabelNotLastCheck implements UnitCheck {

  private static final String SWITCH = "'switch'";
  private static final String DEFAULT = "'default'";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the {@code default} label
   */
  public DefaultLabelNotLastCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      if (visit.node().children().stream().anyMatch(c -> SWITCH.equals(c.kind()))) {
        misplacedDefault(visit.node(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void misplacedDefault(final SyntaxNode switchNode, final List<RuleMatch> matches) {
    final List<SyntaxNode> labels = labels(switchNode);
    for (int index = 0; index < labels.size() - 1; index++) {
      if (isDefault(labels.get(index))) {
        matches.add(CheckSupport.at(labels.get(index).span(), message));
        return;
      }
    }
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

  private static boolean isDefault(final SyntaxNode label) {
    return label.children().stream().anyMatch(c -> DEFAULT.equals(c.kind()));
  }
}
