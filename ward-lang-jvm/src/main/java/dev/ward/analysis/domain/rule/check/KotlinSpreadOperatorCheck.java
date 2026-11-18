package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each argument written with the spread operator, as detekt's {@code SpreadOperator} does,
 * except one that passes on a {@code vararg} parameter of an enclosing function: Kotlin hands that
 * array on without copying it.
 */
public final class KotlinSpreadOperatorCheck implements RuleCheck {
  private static final String ARGUMENT = "valueArgument";
  private static final String FUNCTION = "functionDeclaration";
  private static final String SPREAD = "*";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public KotlinSpreadOperatorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ARGUMENT))) {
      final String text = SyntaxWalk.text(visit.node());
      if (text.startsWith(SPREAD) && !passesOwnVararg(text.substring(1).strip(), visit)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean passesOwnVararg(final String name, final SyntaxWalk.Visit visit) {
    final Pattern declared =
        Pattern.compile("(?<![\\w$])vararg\\s+" + Pattern.quote(name) + "\\s*:");
    return visit.ancestors().stream()
        .filter(ancestor -> FUNCTION.equals(ancestor.kind()))
        .anyMatch(function -> declared.matcher(SyntaxWalk.spacedText(function)).find());
  }
}
