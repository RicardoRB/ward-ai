package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a Kotlin class or object whose lines of code exceed the limit, as detekt's {@code
 * large-class} does: blank and comment lines do not count, and the lines of a nested or inner class
 * belong to that class, so a large nested class does not make its outer class large.
 */
public final class KotlinClassLinesCheck implements RuleCheck {

  private static final Set<String> NESTED =
      Set.of("classDeclaration", "objectDeclaration", "companionObject", "objectLiteral");
  private static final String COMMENT = "comment";

  private final Set<String> kinds;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the class kinds that are measured
   * @param max the largest number of lines a class may have
   * @param message the message; {@code {value}} is the count and {@code {max}} the limit
   */
  public KotlinClassLinesCheck(final Set<String> kinds, final int max, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, kinds).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> lineCount(node) > max)
        .map(
            node ->
                new RuleMatch(
                    node.span(),
                    CheckSupport.fill(
                        message, String.valueOf(lineCount(node)), String.valueOf(max))))
        .toList();
  }

  private static int lineCount(final SyntaxNode node) {
    final Set<Integer> lines = new HashSet<>();
    collect(node, true, lines);
    return lines.size();
  }

  private static void collect(final SyntaxNode node, final boolean root, final Set<Integer> lines) {
    if (!root && NESTED.contains(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      if (!COMMENT.equals(node.kind()) && !node.fields().getOrDefault("text", "").isBlank()) {
        lines.add(node.span().startLine());
      }
      return;
    }
    node.children().forEach(child -> collect(child, false, lines));
  }
}
