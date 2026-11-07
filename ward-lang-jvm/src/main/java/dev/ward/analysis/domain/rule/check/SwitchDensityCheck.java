package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each switch statement whose ratio of statements to labels reaches the threshold, as PMD's
 * switch density does. Statements and labels are counted in the switch's own groups.
 */
public final class SwitchDensityCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String SWITCH_PREFIX = "switch(";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String BLOCK_STATEMENT = "blockStatement";

  private final int threshold;
  private final String message;

  /**
   * Creates the check.
   *
   * @param threshold the ratio of statements to labels at or above which a switch is reported
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public SwitchDensityCheck(final int threshold, final String message) {
    this.threshold = CheckSupport.nonNegative(threshold, "minimum");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(SwitchDensityCheck::isSwitch)
        .flatMap(node -> reportOf(node).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode node) {
    final int labels = countIn(node, LABEL);
    final int statements = countIn(node, BLOCK_STATEMENT);
    if (labels == 0 || statements / labels < threshold) {
      return List.of();
    }
    return List.of(
        CheckSupport.at(
            node.span(),
            CheckSupport.fill(
                message, String.valueOf(statements / labels), String.valueOf(threshold))));
  }

  private static int countIn(final SyntaxNode node, final String kind) {
    return (int)
        node.children().stream()
            .filter(child -> GROUP.equals(child.kind()))
            .flatMap(group -> group.children().stream())
            .filter(child -> kind.equals(child.kind()))
            .count();
  }

  private static boolean isSwitch(final SyntaxNode node) {
    return SyntaxWalk.text(node).startsWith(SWITCH_PREFIX);
  }
}
