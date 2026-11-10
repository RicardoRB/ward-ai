package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code Float.compare} or {@code Double.compare} called with integral arguments that the
 * call widens with a loss of precision, as Error Prone's LossyPrimitiveCompare does. A call whose
 * facts are unknown is never reported.
 */
public final class JavaLossyCompareCheck implements RuleCheck {

  private static final String COMPARE = "compare";
  private static final Map<String, Set<String>> LOSSY =
      Map.of(
          "java.lang.Float", Set.of("int", "long"),
          "java.lang.Double", Set.of("long"));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type of the lossy argument
   */
  public JavaLossyCompareCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      lossy(visit.node(), types)
          .ifPresent(
              type ->
                  matches.add(
                      CheckSupport.at(visit.node().span(), CheckSupport.fill(message, type, ""))));
    }
    return List.copyOf(matches);
  }

  private static Optional<String> lossy(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    if (target.isEmpty() || !COMPARE.equals(target.get().name())) {
      return Optional.empty();
    }
    final Set<String> lossy = LOSSY.getOrDefault(target.get().declaringType().name(), Set.of());
    return JavaCalls.arguments(call).stream()
        .map(argument -> types.typeOf(argument.span()))
        .flatMap(Optional::stream)
        .filter(type -> type.arrayDepth() == 0 && lossy.contains(type.name()))
        .map(TypeRef::name)
        .findFirst();
  }
}
