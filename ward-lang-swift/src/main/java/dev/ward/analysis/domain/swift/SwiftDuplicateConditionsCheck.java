package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reports the links of an {@code if} chain, and the cases of a {@code switch}, that repeat the
 * conditions of another link or case of the same chain, like SwiftLint's {@code
 * duplicate_conditions}. Every repeated link or case is reported at its first condition.
 */
public final class SwiftDuplicateConditionsCheck implements RuleCheck {

  private static final String IF = "if_statement";
  private static final String ELSE = "else_clause";
  private static final String CONDITION_LIST = "condition_list";
  private static final String CONDITION = "condition";
  private static final String SWITCH = "switch_statement";
  private static final String CASE_ITEM_LIST = "case_item_list";
  private static final String PATTERN = "pattern";
  private static final String WHERE = "where_clause";
  private static final Set<String> CASE_CONTAINERS =
      Set.of("switch_cases", "switch_case", "case_label", CASE_ITEM_LIST);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftDuplicateConditionsCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collectChains(root, false, matches);
    return List.copyOf(matches);
  }

  /** Visits every {@code if} that starts a chain, and every {@code switch}. */
  private void collectChains(
      final SyntaxNode node, final boolean isElseIf, final List<RuleMatch> matches) {
    if (IF.equals(node.kind()) && !isElseIf) {
      ifChain(node, matches);
    }
    if (SWITCH.equals(node.kind())) {
      switchCases(node, matches);
    }
    for (final SyntaxNode child : node.children()) {
      collectChains(child, ELSE.equals(node.kind()) && IF.equals(child.kind()), matches);
    }
  }

  private void ifChain(final SyntaxNode first, final List<RuleMatch> matches) {
    final Map<Set<String>, List<SyntaxNode>> byConditions = new LinkedHashMap<>();
    SyntaxNode link = first;
    while (link != null) {
      SwiftTrees.child(link, CONDITION_LIST).ifPresent(list ->
          byConditions
              .computeIfAbsent(conditionsOf(list), key -> new ArrayList<>())
              .add(list));
      link = SwiftTrees.child(link, ELSE).flatMap(clause -> SwiftTrees.child(clause, IF)).orElse(null);
    }
    report(byConditions, matches);
  }

  private static Set<String> conditionsOf(final SyntaxNode list) {
    final Set<String> texts = new TreeSet<>();
    list.children().stream()
        .filter(child -> CONDITION.equals(child.kind()))
        .forEach(child -> texts.add(SwiftTrees.compact(child)));
    return texts;
  }

  private void switchCases(final SyntaxNode switchNode, final List<RuleMatch> matches) {
    final Map<String, List<SyntaxNode>> byCase = new LinkedHashMap<>();
    for (final SyntaxNode child : switchNode.children()) {
      collectCaseItems(child, byCase);
    }
    final Map<Set<String>, List<SyntaxNode>> asSets = new LinkedHashMap<>();
    byCase.forEach((key, items) -> asSets.put(Set.of(key), items));
    report(asSets, matches);
  }

  private static void collectCaseItems(final SyntaxNode node, final Map<String, List<SyntaxNode>> out) {
    if (!CASE_CONTAINERS.contains(node.kind()) && !"switch_statement".equals(node.kind())) {
      return;
    }
    if (CASE_ITEM_LIST.equals(node.kind())) {
      SwiftTrees.child(node, PATTERN).ifPresent(pattern -> {
        final String key =
            SwiftTrees.compact(pattern)
                + SwiftTrees.child(node, WHERE).map(SwiftTrees::compact).orElse("");
        out.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
      });
    }
    for (final SyntaxNode child : node.children()) {
      collectCaseItems(child, out);
    }
  }

  private void report(final Map<Set<String>, List<SyntaxNode>> groups, final List<RuleMatch> matches) {
    groups.values().stream()
        .filter(positions -> positions.size() > 1)
        .flatMap(List::stream)
        .forEach(position -> matches.add(SwiftTrees.at(position, message)));
  }
}
