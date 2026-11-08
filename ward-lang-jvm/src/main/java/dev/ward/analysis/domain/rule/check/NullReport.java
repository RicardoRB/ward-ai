package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reports what the null data-flow of a body finds at a single event: a dereference of a variable
 * that is null or marked possibly null, a null check of a value already dereferenced, a null passed
 * to a call, returned or stored where it must not be.
 */
final class NullReport {

  private final NullScope scope;
  private final NullCalls calls;

  NullReport(
      final UnitTypeFacts types, final NullScope scope, final Map<String, Set<Integer>> callees) {
    this.scope = scope;
    this.calls = new NullCalls(types, scope, callees);
  }

  /** Returns the findings of the events of {@code body}, each looked at on its own. */
  List<NullAnalysis.Finding> direct(final NullSolved body) {
    final List<NullAnalysis.Finding> found = new ArrayList<>();
    body.thrown().forEach((id, entry) -> found.addAll(block(body, id, entry)));
    return found;
  }

  private List<NullAnalysis.Finding> block(
      final NullSolved body, final int id, final Map<Span, Nullity> entry) {
    final List<NullAnalysis.Finding> found = new ArrayList<>();
    final Map<Span, Nullity> any = copy(entry);
    final Map<Span, Nullity> normal =
        body.normal().containsKey(id) ? copy(body.normal().get(id)) : null;
    for (final NullEvent event : body.graph().events().get(id)) {
      examine(body, event, normal, any).ifPresent(found::add);
      NullStates.apply(any, event);
      if (normal != null) {
        NullStates.apply(normal, event);
      }
    }
    return found;
  }

  private static Map<Span, Nullity> copy(final Map<Span, Nullity> state) {
    return new HashMap<>(state);
  }

  private Optional<NullAnalysis.Finding> examine(
      final NullSolved body,
      final NullEvent event,
      final Map<Span, Nullity> normal,
      final Map<Span, Nullity> any) {
    if (event.kind() == NullEvent.Kind.CALL) {
      return calls.passed(body, event, normal);
    }
    final Optional<NullAnalysis.Pattern> pattern =
        switch (event.kind()) {
          case DEREF -> dereference(event, normal, any);
          case COMPARE -> compared(event, normal);
          case RETURN -> returned(body, event, normal);
          case STORE -> stored(body, event, normal);
          default -> Optional.empty();
        };
    return pattern.map(found -> new NullAnalysis.Finding(found, event.node().span()));
  }

  private static Optional<NullAnalysis.Pattern> dereference(
      final NullEvent event, final Map<Span, Nullity> normal, final Map<Span, Nullity> any) {
    final Nullity now = normal == null ? null : normal.get(event.variable());
    final boolean closing = "close".equals(event.name());
    if (now == Nullity.NULL) {
      return Optional.of(
          closing ? NullAnalysis.Pattern.CLOSING_NULL : NullAnalysis.Pattern.ALWAYS_NULL);
    }
    if (any.get(event.variable()) == Nullity.NULL) {
      return Optional.of(
          closing ? NullAnalysis.Pattern.CLOSING_NULL : NullAnalysis.Pattern.ALWAYS_NULL_EXCEPTION);
    }
    return now == Nullity.PARAMETER
        ? Optional.of(NullAnalysis.Pattern.ARGUMENT_MIGHT_BE_NULL)
        : Optional.empty();
  }

  private static Optional<NullAnalysis.Pattern> compared(
      final NullEvent event, final Map<Span, Nullity> normal) {
    return normal != null && normal.get(event.variable()) == Nullity.DEREFED
        ? Optional.of(NullAnalysis.Pattern.WOULD_HAVE_BEEN_NPE)
        : Optional.empty();
  }

  private static Optional<NullAnalysis.Pattern> returned(
      final NullSolved body, final NullEvent event, final Map<Span, Nullity> normal) {
    final String name = body.graph().graph().body();
    final boolean special = name.contains("#clone(") || name.contains("#toString(");
    return !special
            && body.method() != null
            && body.method().nonNullReturn()
            && definitelyNull(event.operand(), normal)
        ? Optional.of(NullAnalysis.Pattern.NONNULL_RETURN_VIOLATION)
        : Optional.empty();
  }

  private Optional<NullAnalysis.Pattern> stored(
      final NullSolved body, final NullEvent event, final Map<Span, Nullity> normal) {
    return scope.nonNullFields().getOrDefault(body.type(), Set.of()).contains(event.name())
            && definitelyNull(event.operand(), normal)
        ? Optional.of(NullAnalysis.Pattern.STORE_INTO_NONNULL_FIELD)
        : Optional.empty();
  }

  /** Tells whether {@code operand} is the literal null or a variable that holds null. */
  static boolean definitelyNull(final NullEvent.Operand operand, final Map<Span, Nullity> state) {
    return operand.isNull()
        || state != null
            && operand.variable() != null
            && state.get(operand.variable()) == Nullity.NULL;
  }
}
