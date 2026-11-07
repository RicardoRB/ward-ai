package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports each parameter that starts a new line in a multi-line function signature at a different
 * column than the first parameter, like SwiftLint's {@code vertical_parameter_alignment}.
 */
public final class SwiftParameterAlignmentCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS =
      Set.of("function_declaration", "initializer_declaration");
  private static final Set<String> BODIES =
      Set.of("function_body", "initializer_body", "code_block");
  private static final String PARAMETER = "parameter";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftParameterAlignmentCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    functions(root, matches);
    return List.copyOf(matches);
  }

  private void functions(final SyntaxNode node, final List<RuleMatch> matches) {
    if (FUNCTIONS.contains(node.kind())) {
      final List<SyntaxNode> parameters = new ArrayList<>();
      parameters(node, parameters);
      misaligned(parameters, matches);
    }
    node.children().forEach(child -> functions(child, matches));
  }

  private static void parameters(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (PARAMETER.equals(child.kind())) {
        out.add(child);
      } else if (!BODIES.contains(child.kind())) {
        parameters(child, out);
      }
    }
  }

  private void misaligned(final List<SyntaxNode> parameters, final List<RuleMatch> matches) {
    SyntaxNode previous = null;
    for (final SyntaxNode parameter : parameters) {
      final boolean newLine =
          previous != null && parameter.span().startLine() != previous.span().startLine();
      if (newLine && parameter.span().startColumn() != parameters.get(0).span().startColumn()) {
        matches.add(new RuleMatch(parameter.span(), message));
      }
      previous = parameter;
    }
  }
}
