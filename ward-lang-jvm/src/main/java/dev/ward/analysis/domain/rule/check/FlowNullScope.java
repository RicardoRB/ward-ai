package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the transfer functions of one graph share: which variable a name in the syntax is, the
 * declarations of the file, the method the graph belongs to, and where findings go.
 */
final class FlowNullScope {

  private final Map<String, Span> byPosition = new HashMap<>();
  private final Map<String, Set<Span>> byName = new HashMap<>();
  private final Set<Span> captured = new HashSet<>();
  private final FlowNullIndex index;
  private final Optional<FlowNullIndex.Method> method;
  private final List<FlowNullEvent> events;

  FlowNullScope(
      final ControlFlowGraph graph, final FlowNullIndex index, final List<FlowNullEvent> events) {
    this.index = index;
    this.events = events;
    this.method = index.enclosing(graph.span());
    for (final BasicBlock block : graph.blocks()) {
      for (final VariableAccess access : block.accesses()) {
        if (access.kind() == AccessKind.CAPTURE) {
          captured.add(access.declaration());
        }
        byPosition.put(key(access.name(), access.span()), access.declaration());
        if (access.kind() == AccessKind.DECLARE || access.kind() == AccessKind.PARAMETER) {
          byName.merge(access.name(), Set.of(access.declaration()), FlowNullScope::union);
        }
      }
    }
  }

  private static Set<Span> union(final Set<Span> first, final Set<Span> second) {
    final Set<Span> all = new HashSet<>(first);
    all.addAll(second);
    return all;
  }

  private static String key(final String name, final Span span) {
    return name + ":" + span.startOffset();
  }

  /**
   * The declaration of the variable that {@code node}, a simple name, refers to, if it is local. A
   * variable that a nested body captures is not tracked, because that body may change it.
   */
  Optional<Span> variable(final SyntaxNode node) {
    return resolve(node).filter(declaration -> !captured.contains(declaration));
  }

  private Optional<Span> resolve(final SyntaxNode node) {
    final String name = SyntaxWalk.text(node);
    final Span exact = byPosition.get(key(name, node.span()));
    if (exact != null) {
      return Optional.of(exact);
    }
    final Set<Span> only = byName.getOrDefault(name, Set.of());
    return only.size() == 1 ? only.stream().findFirst() : Optional.empty();
  }

  FlowNullIndex index() {
    return index;
  }

  Optional<FlowNullIndex.Method> method() {
    return method;
  }

  /** Records a finding; the iteration passes of the analysis record none. */
  void report(final FlowNullEvent event) {
    if (events != null) {
      events.add(event);
    }
  }
}
