package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code 'use strict'} directive, like ESLint {@code strict} in module code: a module is
 * strict already, so every directive of a program or a function body is unnecessary. The rule is
 * applied to modules because Ward analyses JavaScript and TypeScript as modules; the mode option of
 * ESLint, which only changes script code, is not offered.
 */
public final class NodeStrictDirectiveCheck implements RuleCheck {

  private static final String SOURCE_ELEMENTS = "sourceElements";
  private static final Set<String> DIRECTIVES = Set.of("\"use strict\"", "'use strict'");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each directive
   */
  public NodeStrictDirectiveCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (SOURCE_ELEMENTS.equals(node.kind())) {
      prologue(node, matches);
    }
    node.children().forEach(child -> collect(child, matches));
  }

  /** The text of a statement without its semicolon. */
  private static String statementText(final SyntaxNode element) {
    final String text = LeafText.of(element);
    return text.endsWith(";") ? text.substring(0, text.length() - 1) : text;
  }

  /** The leading statements of a body that are the directive, each reported. */
  private void prologue(final SyntaxNode elements, final List<RuleMatch> matches) {
    for (final SyntaxNode element : elements.children()) {
      if (!ExpressionShapes.isStringElement(element)) {
        return;
      }
      if (DIRECTIVES.contains(statementText(element))) {
        matches.add(new RuleMatch(element.span(), message));
      }
    }
  }
}
