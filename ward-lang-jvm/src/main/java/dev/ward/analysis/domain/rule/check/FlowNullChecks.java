package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** The null checks of the transfer function: dereferences, instanceof tests and comparisons. */
final class FlowNullChecks {

  private final FlowNullScope scope;

  FlowNullChecks(final FlowNullScope scope) {
    this.scope = scope;
  }

  void dereference(final SyntaxNode base, final String method, final FlowNullState state) {
    if (!FlowNullValues.isName(base)) {
      return;
    }
    scope
        .variable(base)
        .ifPresent(
            decl -> {
              final FlowNullState.Fact fact = state.get(decl);
              final Optional<FlowNullEvent.Kind> kind = kind(fact, "close".equals(method));
              kind.ifPresent(
                  found ->
                      scope.report(FlowNullEvent.at(found, base.span(), SyntaxWalk.text(base))));
              state.put(decl, FlowNullValue.DEREFERENCED, fact.exception());
            });
  }

  private static Optional<FlowNullEvent.Kind> kind(
      final FlowNullState.Fact fact, final boolean closing) {
    if (fact.value().isNullish()) {
      if (closing) {
        return Optional.of(FlowNullEvent.Kind.CLOSE_NULL);
      }
      return Optional.of(
          fact.exception()
              ? FlowNullEvent.Kind.DEREF_NULL_EXCEPTION
              : FlowNullEvent.Kind.DEREF_NULL);
    }
    if (fact.value() == FlowNullValue.MIXED) {
      return Optional.of(
          fact.exception()
              ? FlowNullEvent.Kind.DEREF_MIXED_EXCEPTION
              : FlowNullEvent.Kind.DEREF_MIXED);
    }
    return Optional.empty();
  }

  void instanceOf(final SyntaxNode operand, final FlowNullState state) {
    if (FlowNullValues.of(operand, state, scope).isNullish()) {
      scope.report(
          FlowNullEvent.at(
              FlowNullEvent.Kind.INSTANCEOF_NULL, operand.span(), SyntaxWalk.text(operand)));
    }
  }

  void comparison(final List<SyntaxNode> parts, final FlowNullState state) {
    final SyntaxNode left = parts.get(0);
    final SyntaxNode right = parts.get(2);
    final FlowNullValue first = FlowNullValues.of(left, state, scope);
    final FlowNullValue second = FlowNullValues.of(right, state, scope);
    final boolean nullTest =
        "null".equals(SyntaxWalk.text(left)) || "null".equals(SyntaxWalk.text(right));
    if (nullTest && (first == FlowNullValue.DEREFERENCED || second == FlowNullValue.DEREFERENCED)) {
      final SyntaxNode tested = first == FlowNullValue.DEREFERENCED ? left : right;
      scope.report(
          FlowNullEvent.at(
              FlowNullEvent.Kind.CHECK_AFTER_DEREF, tested.span(), SyntaxWalk.text(tested)));
    }
    if (first == FlowNullValue.NEVER && second == FlowNullValue.NON_NULL
        || first == FlowNullValue.NON_NULL && second == FlowNullValue.NEVER) {
      scope.report(
          FlowNullEvent.at(
              FlowNullEvent.Kind.COMPARE_INCOMPATIBLE, left.span(), SyntaxWalk.text(left)));
    }
  }
}
