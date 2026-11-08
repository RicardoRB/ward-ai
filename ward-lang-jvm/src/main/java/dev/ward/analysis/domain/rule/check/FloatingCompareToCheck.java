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
import java.util.regex.Pattern;

/**
 * Reports the comparison idiom {@code a < b ? -1 : a > b ? 1 : 0} inside a {@code compareTo} or
 * {@code compare} method when the operands are {@code float} or {@code double}: it orders NaN and
 * negative zero wrongly, so {@code Double.compare} should be used. It reports nothing when the type
 * of the operand is unknown.
 */
public final class FloatingCompareToCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD = "methodDeclaration";
  private static final Set<String> COMPARATORS = Set.of("compareTo", "compare");
  private static final Set<String> FLOATING =
      Set.of("float", "double", "java.lang.Float", "java.lang.Double");
  private static final Pattern IDIOM =
      Pattern.compile("^(.+)<(.+)\\?-1:\\1>\\2\\?1:0$|^(.+)>(.+)\\?1:\\3<\\4\\?-1:0$");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the conditional
   */
  public FloatingCompareToCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .filter(visit -> IDIOM.matcher(SyntaxWalk.text(visit.node())).matches())
        .filter(visit -> inComparator(visit.ancestors()) && floating(visit.node(), types))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean inComparator(final List<SyntaxNode> ancestors) {
    return ancestors.stream()
        .filter(node -> METHOD.equals(node.kind()))
        .reduce((outer, inner) -> inner)
        .filter(method -> COMPARATORS.contains(MemberDeclarations.name(method)))
        .isPresent();
  }

  private static boolean floating(final SyntaxNode conditional, final UnitTypeFacts types) {
    final SyntaxNode condition = conditional.children().get(0);
    return !condition.children().isEmpty()
        && types
            .typeOf(condition.children().get(0).span())
            .filter(type -> type.arrayDepth() == 0 && FLOATING.contains(type.name()))
            .isPresent();
  }
}
