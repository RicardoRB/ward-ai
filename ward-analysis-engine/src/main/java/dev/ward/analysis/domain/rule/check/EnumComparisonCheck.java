package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an {@code equals} call on an enum receiver or with an enum argument. Enum constants are
 * compared by identity, so {@code ==} is the intended comparison.
 */
public final class EnumComparisonCheck implements RuleCheck {

  private static final String EQUALS = "equals";
  private static final String EXPRESSION = "expression";
  private static final String ARGUMENTS = "arguments";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public EnumComparisonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public FactNeed needs() {
    return TypedChecks.CALLS_AND_SHAPES;
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final dev.ward.analysis.domain.rule.FlowView flow,
      final dev.ward.analysis.domain.rule.ScopeView scopes,
      final UnitTypeFacts types,
      final dev.ward.analysis.domain.ir.SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode call = visit.node();
      if (TypedChecks.calledName(call, types).filter(EQUALS::equals).isPresent()
          && comparesEnum(call, types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean comparesEnum(final SyntaxNode call, final UnitTypeFacts types) {
    final boolean receiver =
        call.children().stream()
            .filter(child -> EXPRESSION.equals(child.kind()))
            .findFirst()
            .filter(child -> TypedChecks.isEnum(child, types))
            .isPresent();
    return receiver
        || SyntaxWalk.descendants(call).stream()
            .filter(node -> ARGUMENTS.equals(node.kind()))
            .flatMap(args -> SyntaxWalk.descendants(args).stream())
            .filter(node -> EXPRESSION.equals(node.kind()))
            .anyMatch(node -> TypedChecks.isEnum(node, types));
  }
}
