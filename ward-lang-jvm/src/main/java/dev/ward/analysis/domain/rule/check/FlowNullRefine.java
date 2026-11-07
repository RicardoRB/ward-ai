package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Narrows what is known about a variable along the true or false edge of a null test. */
final class FlowNullRefine {

  private static final String NULL = "null";
  private static final int BINARY = 3;

  private FlowNullRefine() {}

  /** Returns {@code state} narrowed by the outcome {@code edge} of {@code condition}. */
  static FlowNullState along(
      final SyntaxNode condition,
      final FlowEdgeKind edge,
      final FlowNullState state,
      final FlowNullScope scope) {
    if (edge != FlowEdgeKind.TRUE && edge != FlowEdgeKind.FALSE) {
      return state;
    }
    final FlowNullState out = state.copy();
    narrow(condition, edge == FlowEdgeKind.TRUE, out, scope);
    return out;
  }

  private static void narrow(
      final SyntaxNode condition,
      final boolean outcome,
      final FlowNullState state,
      final FlowNullScope scope) {
    final List<SyntaxNode> parts = condition.children();
    if (parts.size() == 1 && !parts.get(0).children().isEmpty()) {
      narrow(parts.get(0), outcome, state, scope);
    } else if (parts.size() == BINARY && "(".equals(SyntaxWalk.text(parts.get(0)))) {
      narrow(parts.get(1), outcome, state, scope);
    } else if (parts.size() == 2 && "!".equals(SyntaxWalk.text(parts.get(0)))) {
      narrow(parts.get(1), !outcome, state, scope);
    } else if (parts.size() == BINARY) {
      binary(parts, outcome, state, scope);
    }
  }

  private static void binary(
      final List<SyntaxNode> parts,
      final boolean outcome,
      final FlowNullState state,
      final FlowNullScope scope) {
    final String operator = SyntaxWalk.text(parts.get(1));
    if ("instanceof".equals(operator) && outcome) {
      set(parts.get(0), FlowNullValue.NON_NULL, state, scope);
    } else if ("==".equals(operator) || "!=".equals(operator)) {
      final SyntaxNode other = nullTested(parts);
      if (other != null) {
        final boolean isNull = outcome == "==".equals(operator);
        set(other, isNull ? FlowNullValue.NULL : FlowNullValue.NON_NULL, state, scope);
      }
    }
  }

  /** The side of a comparison that is not the literal null, if the other one is. */
  private static SyntaxNode nullTested(final List<SyntaxNode> parts) {
    if (isNull(parts.get(2))) {
      return parts.get(0);
    }
    return isNull(parts.get(0)) ? parts.get(2) : null;
  }

  private static boolean isNull(final SyntaxNode node) {
    return NULL.equals(SyntaxWalk.text(node));
  }

  private static void set(
      final SyntaxNode name,
      final FlowNullValue value,
      final FlowNullState state,
      final FlowNullScope scope) {
    if (FlowNullValues.isName(name)) {
      scope
          .variable(name)
          .ifPresent(
              declaration -> {
                final FlowNullValue known = state.get(declaration).value();
                final boolean keep = value == FlowNullValue.NULL && known.isNonNull();
                state.put(declaration, keep ? known : value, state.get(declaration).exception());
              });
    }
  }
}
