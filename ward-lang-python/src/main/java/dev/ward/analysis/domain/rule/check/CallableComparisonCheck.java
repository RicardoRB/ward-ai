package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a comparison of a module function, not called, with a value that is not one, like Pylint
 * {@code comparison-with-callable}. A function that raises is not a bare callable here, as in
 * Pylint; a function with any raise in its own body is left out.
 */
public final class CallableComparisonCheck implements RuleCheck {

  private static final Set<String> COMPARISONS = Set.of("comparison");
  private static final int SINGLE_CALLABLE = 1;
  private static final int LEFT = 1;
  private static final int RIGHT = 3;
  private static final Set<String> RAISES = Set.of("raise_stmt");
  private static final Pattern COMPARISON =
      Pattern.compile("^([\\w.]+)(==|!=|<=|>=|<|>)([\\w.]+)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public CallableComparisonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, COMPARISONS)) {
      final Matcher comparison = COMPARISON.matcher(SyntaxWalk.text(visit.node()));
      if (comparison.matches()) {
        final int bare =
            (bareCallable(definitions, comparison.group(LEFT)) ? 1 : 0)
                + (bareCallable(definitions, comparison.group(RIGHT)) ? 1 : 0);
        if (bare == SINGLE_CALLABLE) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static boolean bareCallable(
      final Map<String, SyntaxNode> definitions, final String operand) {
    final SyntaxNode definition = definitions.get(operand);
    return definition != null
        && SyntaxKinds.FUNCDEF.equals(definition.kind())
        && FunctionBodies.ownScope(definition, RAISES).isEmpty();
  }
}
