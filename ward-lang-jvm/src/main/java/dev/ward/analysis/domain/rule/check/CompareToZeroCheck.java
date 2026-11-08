package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports a comparison of the result of {@code compareTo} or {@code compare} with an integer
 * literal other than zero: whether a type returns only -1, 0 and 1 is an implementation detail. It
 * reports nothing when the call is unknown.
 */
public final class CompareToZeroCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final int OPERANDS = 3;
  private static final Set<String> OPERATORS = Set.of("==", "!=", "<", ">", "<=", ">=");
  private static final Set<String> NAMES = Set.of("compareTo", "compare");
  private static final Pattern NONZERO = Pattern.compile("-?(?!0+L?$)\\d+L?");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the comparison
   */
  public CompareToZeroCheck(final String message) {
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
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      if (reported(visit.node(), types)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean reported(final SyntaxNode node, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() < OPERANDS) {
      return false;
    }
    final SyntaxNode left = parts.get(0);
    final SyntaxNode right = parts.get(parts.size() - 1);
    final String operator =
        parts.subList(1, parts.size() - 1).stream()
            .map(SyntaxWalk::text)
            .collect(Collectors.joining());
    return OPERATORS.contains(operator)
        && (isCompare(left, types) && nonzero(right) || isCompare(right, types) && nonzero(left));
  }

  private static boolean isCompare(final SyntaxNode operand, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(operand.span());
    return target.isPresent()
        && NAMES.contains(target.get().name())
        && target.get().returnType() != null
        && "int".equals(target.get().returnType().name());
  }

  private static boolean nonzero(final SyntaxNode operand) {
    return NONZERO.matcher(SyntaxWalk.text(operand)).matches();
  }
}
