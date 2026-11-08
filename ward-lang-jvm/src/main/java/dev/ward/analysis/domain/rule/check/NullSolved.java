package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The null data-flow of one body, solved: the events, the state without and with the edges that
 * statements take when they throw, and what every path dereferences.
 *
 * @param graph the body
 * @param type the simple name of the type that declares it, or empty
 * @param method the method, if the file declares it unambiguously
 * @param normal the state at the start of each block that normal control flow reaches
 * @param thrown the state at the start of each block that any flow reaches
 * @param backward what every path from each point dereferences
 */
record NullSolved(
    NullGraph graph,
    String type,
    NullScope.Method method,
    Map<Integer, Map<Span, Nullity>> normal,
    Map<Integer, Map<Span, Nullity>> thrown,
    NullBackward backward) {

  /** Solves {@code body}, whose name is {@code Type#name(count)} or a lambda. */
  static NullSolved of(
      final ControlFlowGraph body, final NullScope scope, final UnitTypeFacts types) {
    final NullGraph graph = NullGraph.of(body);
    final int hash = body.body().indexOf('#');
    final String owner = hash < 0 ? "" : body.body().substring(0, hash);
    final String type =
        owner.substring(Math.max(owner.lastIndexOf('.'), owner.lastIndexOf('$')) + 1);
    final NullScope.Method method =
        hash < 0 ? null : scope.methods().get(type + body.body().substring(hash));
    final Map<Span, Nullity> initial = initial(body, scope);
    final NullBackward backward = new NullBackward(graph, new Needs(types, scope, type, method));
    return new NullSolved(
        graph,
        type,
        method,
        new NullForward(graph, false).entries(initial),
        new NullForward(graph, true).entries(initial),
        backward);
  }

  private static Map<Span, Nullity> initial(final ControlFlowGraph body, final NullScope scope) {
    final Map<Span, Nullity> state = new HashMap<>();
    for (final VariableAccess access : parameters(body)) {
      if (scope.nullableParameters().contains(access.declaration().startOffset())) {
        state.put(access.declaration(), Nullity.PARAMETER);
      }
    }
    return state;
  }

  /** Returns the parameter accesses of the body, in the order of the parameters. */
  static List<VariableAccess> parameters(final ControlFlowGraph body) {
    final BasicBlock entry = body.blocks().get(body.entry());
    return entry.accesses().stream().filter(a -> a.kind() == AccessKind.PARAMETER).toList();
  }

  /** The uses that need a value that is not null, as the annotations of the file and calls say. */
  record Needs(UnitTypeFacts types, NullScope scope, String type, NullScope.Method method)
      implements NullBackward.Needs {

    @Override
    public NullBackward.Use argument(final NullEvent call, final int index) {
      final boolean required =
          types
              .callTarget(call.node().span())
              .map(CallTarget::parameterAnnotations)
              .filter(all -> index < all.size())
              .map(all -> all.get(index))
              .filter(Needs::nonNull)
              .isPresent();
      return required ? NullBackward.Use.PARAMETER : null;
    }

    @Override
    public boolean returns() {
      return method != null && method.nonNullReturn();
    }

    @Override
    public boolean field(final String name) {
      return scope.nonNullFields().getOrDefault(type, Set.of()).contains(name);
    }

    private static boolean nonNull(final List<String> annotations) {
      return annotations.stream()
          .map(name -> name.substring(name.lastIndexOf('.') + 1))
          .anyMatch(NullScope.NON_NULL::contains);
    }
  }
}
