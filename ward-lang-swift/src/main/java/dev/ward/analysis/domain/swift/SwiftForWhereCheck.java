package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code for} loop whose body is only one {@code if} without {@code else}, a binding or a
 * pattern, and a single condition, where a {@code where} clause reads better, like SwiftLint's
 * {@code for_where}. The {@code if} is reported.
 */
public final class SwiftForWhereCheck implements RuleCheck {

  private static final String FOR_IN = "for_in_statement";
  private static final String IF = "if_statement";
  private static final String BODY = "code_block";
  private static final String WHERE = "where_clause";
  private static final String ELSE = "else_clause";
  private static final String CONDITION_LIST = "condition_list";
  private static final String CONDITION = "condition";
  private static final String STATEMENT = "statement";
  private static final Set<String> STATEMENT_LISTS = Set.of("statements", "statements_impl");
  private static final Set<String> PATTERNS = Set.of("optional_binding_condition", "case_condition");
  private static final Set<String> COMBINATORS = Set.of("&&", "||");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftForWhereCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode loop : SwiftTrees.descendants(root, FOR_IN)) {
      onlyIf(loop)
          .filter(SwiftForWhereCheck::isReportable)
          .ifPresent(branch -> matches.add(SwiftTrees.at(branch, message)));
    }
    return List.copyOf(matches);
  }

  /** Returns the {@code if} that is the only statement of the loop body, when there is one. */
  private static Optional<SyntaxNode> onlyIf(final SyntaxNode loop) {
    if (SwiftTrees.child(loop, WHERE).isPresent()) {
      return Optional.empty();
    }
    return SwiftTrees.child(loop, BODY)
        .map(body -> statements(body, new ArrayList<>()))
        .filter(statements -> statements.size() == 1)
        .flatMap(statements -> SwiftTrees.descendants(statements.get(0), IF).stream().findFirst());
  }

  /** Collects the statements of a block, not those nested inside them. */
  private static List<SyntaxNode> statements(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        out.add(child);
      } else if (STATEMENT_LISTS.contains(child.kind())) {
        statements(child, out);
      }
    }
    return out;
  }

  private static boolean isReportable(final SyntaxNode branch) {
    return SwiftTrees.child(branch, ELSE).isEmpty() && hasSingleSimpleCondition(branch);
  }

  private static boolean hasSingleSimpleCondition(final SyntaxNode branch) {
    final List<SyntaxNode> conditions =
        SwiftTrees.child(branch, CONDITION_LIST)
            .map(list -> list.children().stream().filter(c -> CONDITION.equals(c.kind())).toList())
            .orElse(List.of());
    if (conditions.size() != 1) {
      return false;
    }
    final SyntaxNode condition = conditions.get(0);
    return PATTERNS.stream().allMatch(kind -> SwiftTrees.descendants(condition, kind).isEmpty())
        && COMBINATORS.stream().noneMatch(token -> SwiftTrees.compact(condition).contains(token));
  }
}
