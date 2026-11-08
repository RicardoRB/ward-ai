package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Reports a null that a call passes to a parameter that must not be null: one the parameter's
 * annotation forbids, or one that the called method of the file dereferences on every path.
 */
final class NullCalls {

  private final UnitTypeFacts types;
  private final NullScope scope;
  private final Map<String, Set<Integer>> callees;

  NullCalls(
      final UnitTypeFacts types, final NullScope scope, final Map<String, Set<Integer>> callees) {
    this.types = types;
    this.scope = scope;
    this.callees = Map.copyOf(callees);
  }

  /** Looks at the arguments of {@code call} that are certainly null. */
  Optional<NullAnalysis.Finding> passed(
      final NullSolved body, final NullEvent call, final Map<Span, Nullity> normal) {
    final Optional<CallTarget> target = types.callTarget(call.node().span());
    if (target.isEmpty() || isEquals(target.get())) {
      return Optional.empty();
    }
    return IntStream.range(0, call.args().size())
        .filter(index -> NullReport.definitelyNull(call.args().get(index).operand(), normal))
        .mapToObj(index -> parameter(body, call, target.get(), index))
        .flatMap(Optional::stream)
        .findFirst()
        .map(pattern -> new NullAnalysis.Finding(pattern, call.node().span()));
  }

  private Optional<NullAnalysis.Pattern> parameter(
      final NullSolved body, final NullEvent call, final CallTarget target, final int index) {
    if (new NullSolved.Needs(types, scope, body.type(), body.method()).argument(call, index)
        != null) {
      return Optional.of(NullAnalysis.Pattern.NONNULL_PARAM_VIOLATION);
    }
    final String key =
        NullScope.key(target.declaringType().name(), target.name(), target.parameters().size());
    final NullScope.Method callee = scope.methods().get(key);
    if (callee == null || !callees.getOrDefault(key, Set.of()).contains(index)) {
      return Optional.empty();
    }
    if (callee.isPrivate() || callee.isStatic() || callee.constructor()) {
      return Optional.of(NullAnalysis.Pattern.NULL_PARAM_DEREF_NONVIRTUAL);
    }
    return callee.isFinal() || callee.closedType()
        ? Optional.of(NullAnalysis.Pattern.NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS)
        : Optional.empty();
  }

  private static boolean isEquals(final CallTarget target) {
    return "equals".equals(target.name()) && target.parameters().size() == 1;
  }
}
