package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a Kotlin call that is a whole statement, so its result is dropped, when the called method
 * has one of the return-value annotations and none of the ignore annotations. A pattern {@code
 * *.Name} matches any annotation whose name ends with {@code .Name}. A call whose target is unknown
 * is never reported.
 */
public final class KotlinIgnoredReturnValueCheck implements RuleCheck {
  private static final String STATEMENT = "statement";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String WILDCARD = "*";
  private static final FactNeed NEED = new FactNeed(Set.of(POSTFIX), Set.of(FactKind.CALL));

  private final Set<String> returnValue;
  private final Set<String> ignored;
  private final String message;

  /**
   * Creates the check.
   *
   * @param returnValue the annotation patterns that mark a return value as one to use
   * @param ignored the annotation patterns that allow the result to be dropped
   * @param message the message reported at the call
   */
  public KotlinIgnoredReturnValueCheck(
      final Set<String> returnValue, final Set<String> ignored, final String message) {
    this.returnValue = Set.copyOf(returnValue);
    this.ignored = Set.copyOf(ignored);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(statement -> KotlinStatementLists.expression(statement, POSTFIX).stream())
        .filter(call -> isReported(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private boolean isReported(final SyntaxNode call, final UnitTypeFacts types) {
    return types
        .callTarget(call.span())
        .filter(
            target ->
                anyMatches(returnValue, target.annotations())
                    && !anyMatches(ignored, target.annotations()))
        .isPresent();
  }

  private static boolean anyMatches(final Set<String> patterns, final List<String> names) {
    return names.stream()
        .anyMatch(name -> patterns.stream().anyMatch(pattern -> matches(pattern, name)));
  }

  private static boolean matches(final String pattern, final String name) {
    return pattern.startsWith(WILDCARD)
        ? name.endsWith(pattern.substring(WILDCARD.length()))
        : pattern.equals(name);
  }
}
