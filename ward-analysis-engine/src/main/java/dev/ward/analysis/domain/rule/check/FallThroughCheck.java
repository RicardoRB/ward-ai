package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a case group with statements whose last statement neither leaves the switch nor continues
 * the loop, when another case group or trailing label follows it. The check reports at the label
 * that the falling-through group runs into. The user option is {@code check-last-group}, which also
 * checks the last group. Shortcut: a statement counts as leaving only when it is a {@code break},
 * {@code continue}, {@code return}, {@code throw} or {@code yield} statement; an if statement whose
 * branches all leave is still reported.
 */
public final class FallThroughCheck implements UnitCheck {

  private static final String SWITCH = "'switch'";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String STATEMENT = "blockStatement";
  private static final Pattern LEAVING =
      Pattern.compile("return.*|throw.*|yield.*|(?:break|continue)[\\w$]*;.*", Pattern.DOTALL);

  private final boolean checkLastGroup;
  private final String message;

  /**
   * Creates the check.
   *
   * @param checkLastGroup whether the last case group must leave the switch too
   * @param message the message reported at the label that is run into
   */
  public FallThroughCheck(final boolean checkLastGroup, final String message) {
    this.checkLastGroup = checkLastGroup;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      if (visit.node().children().stream().anyMatch(c -> SWITCH.equals(c.kind()))) {
        fallThroughs(visit.node(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void fallThroughs(final SyntaxNode switchNode, final List<RuleMatch> matches) {
    final List<Group> groups = groups(switchNode);
    for (int index = 0; index < groups.size(); index++) {
      final Group group = groups.get(index);
      final boolean last = index == groups.size() - 1;
      if (group.statements().isEmpty() || last && !checkLastGroup) {
        continue;
      }
      final SyntaxNode lastStatement = group.statements().get(group.statements().size() - 1);
      if (!LEAVING.matcher(SyntaxWalk.text(lastStatement)).matches()) {
        matches.add(CheckSupport.at(reportSpan(groups, index, group), message));
      }
    }
  }

  private static Span reportSpan(final List<Group> groups, final int index, final Group group) {
    if (index + 1 < groups.size()) {
      return groups.get(index + 1).labels().get(0).span();
    }
    return group.statements().get(group.statements().size() - 1).span();
  }

  private static List<Group> groups(final SyntaxNode switchNode) {
    final List<Group> groups = new ArrayList<>();
    for (final SyntaxNode child : switchNode.children()) {
      if (GROUP.equals(child.kind())) {
        groups.add(group(child));
      } else if (LABEL.equals(child.kind())) {
        groups.add(new Group(List.of(child), List.of()));
      }
    }
    return groups;
  }

  private static Group group(final SyntaxNode node) {
    final List<SyntaxNode> labels = new ArrayList<>();
    final List<SyntaxNode> statements = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (LABEL.equals(child.kind())) {
        labels.add(child);
      } else if (STATEMENT.equals(child.kind())) {
        statements.add(child);
      }
    }
    return new Group(List.copyOf(labels), List.copyOf(statements));
  }

  private record Group(List<SyntaxNode> labels, List<SyntaxNode> statements) {}
}
