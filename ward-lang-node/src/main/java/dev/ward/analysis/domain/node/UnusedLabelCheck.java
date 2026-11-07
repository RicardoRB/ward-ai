package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each label that no {@code break} or {@code continue} below it names, like ESLint {@code
 * no-unused-labels}.
 */
public final class UnusedLabelCheck implements RuleCheck {

  private static final String LABELLED = "labelledStatement";
  private static final String IDENTIFIER = "identifier";
  private static final Set<String> JUMPS = Set.of("breakStatement", "continueStatement");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the label
   */
  public UnusedLabelCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (LABELLED.equals(node.kind())) {
      label(node)
          .filter(name -> !jumps(node, name))
          .ifPresent(
              name -> found.add(new RuleMatch(node.span(), message.replace("{value}", name))));
    }
    node.children().forEach(child -> collect(child, found));
  }

  private static Optional<String> label(final SyntaxNode labelled) {
    return labelled.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(UnusedLabelCheck::text);
  }

  private static boolean jumps(final SyntaxNode node, final String name) {
    return node.children().stream().anyMatch(child -> names(child, name) || jumps(child, name));
  }

  private static boolean names(final SyntaxNode node, final String name) {
    return JUMPS.contains(node.kind())
        && node.children().stream()
            .anyMatch(child -> IDENTIFIER.equals(child.kind()) && name.equals(text(child)));
  }

  private static String text(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return String.valueOf(node.fields().getOrDefault("text", ""));
    }
    final StringBuilder out = new StringBuilder();
    node.children().forEach(child -> out.append(text(child)));
    return out.toString();
  }
}
