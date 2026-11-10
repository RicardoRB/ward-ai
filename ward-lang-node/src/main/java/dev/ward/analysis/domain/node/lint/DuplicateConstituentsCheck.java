package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports a union or intersection constituent that repeats an earlier constituent of the same type.
 */
public final class DuplicateConstituentsCheck implements RuleCheck {

  private static final String OPERATION = "unionOrIntersectionOrPrimaryType";
  private static final String UNION = "|";
  private static final String INTERSECTION = "&";
  private static final String TYPE = "{type}";
  private static final String PREVIOUS = "{previous}";

  private final boolean ignoreUnions;
  private final boolean ignoreIntersections;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public DuplicateConstituentsCheck(
      final boolean ignoreUnions, final boolean ignoreIntersections, final String message) {
    this.ignoreUnions = ignoreUnions;
    this.ignoreIntersections = ignoreIntersections;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    visit(root, out);
    return out;
  }

  private void visit(final SyntaxNode node, final List<RuleMatch> out) {
    final String operator = OPERATION.equals(node.kind()) ? operatorOf(node) : null;
    if (operator == null) {
      node.children().forEach(child -> visit(child, out));
      return;
    }
    final List<SyntaxNode> constituents = new ArrayList<>();
    gather(node, operator, constituents);
    if (!isIgnored(operator)) {
      checkChain(out, constituents);
    }
    constituents.forEach(constituent -> visit(constituent, out));
  }

  private void checkChain(final List<RuleMatch> out, final List<SyntaxNode> constituents) {
    final Map<String, String> seen = new HashMap<>();
    for (final SyntaxNode constituent : constituents) {
      final String text = SyntaxText.of(constituent);
      final String previous = seen.putIfAbsent(text, text);
      if (previous != null) {
        out.add(new RuleMatch(constituent.span(), fill(text)));
      }
    }
  }

  private boolean isIgnored(final String operator) {
    return UNION.equals(operator) ? ignoreUnions : ignoreIntersections;
  }

  private String fill(final String type) {
    return message.replace(TYPE, type).replace(PREVIOUS, type);
  }

  private static String operatorOf(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (SyntaxText.isToken(child, UNION)) {
        return UNION;
      }
      if (SyntaxText.isToken(child, INTERSECTION)) {
        return INTERSECTION;
      }
    }
    return null;
  }

  private static void gather(
      final SyntaxNode node, final String operator, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (OPERATION.equals(child.kind()) && operator.equals(operatorOf(child))) {
        gather(child, operator, out);
      } else if (!SyntaxText.isToken(child, operator)) {
        out.add(child);
      }
    }
  }
}
