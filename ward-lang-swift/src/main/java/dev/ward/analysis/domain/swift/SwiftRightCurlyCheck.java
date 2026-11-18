package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports an {@code else} or {@code catch} that starts a line after the closing brace of the
 * block before it, where Google's style keeps it on the brace's line. The {@code else} of a
 * {@code guard} has no brace before it and is not reported.
 */
public final class SwiftRightCurlyCheck implements RuleCheck {
  private static final List<String> CLAUSES = List.of("else_clause", "catch_clause");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftRightCurlyCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> leaves = SwiftTrees.leaves(root);
    final Map<Integer, Integer> indexByOffset = new HashMap<>();
    for (int index = 0; index < leaves.size(); index++) {
      indexByOffset.put(leaves.get(index).span().startOffset(), index);
    }
    final List<RuleMatch> matches = new ArrayList<>();
    for (final String kind : CLAUSES) {
      for (final SyntaxNode clause : SwiftTrees.descendants(root, kind)) {
        final Integer index = indexByOffset.get(clause.span().startOffset());
        if (index != null && index > 0 && startsLine(clause, leaves.get(index - 1))) {
          matches.add(SwiftTrees.at(clause, message));
        }
      }
    }
    matches.sort((a, b) -> Integer.compare(a.span().startOffset(), b.span().startOffset()));
    return List.copyOf(matches);
  }

  private static boolean startsLine(final SyntaxNode clause, final SyntaxNode previous) {
    return "}".equals(previous.fields().get("text"))
        && previous.span().endLine() < clause.span().startLine();
  }
}
