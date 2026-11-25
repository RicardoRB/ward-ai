package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Lets a method that sets the content type of a response, or its {@code X-Content-Type-Options},
 * vouch for the response of its callers. A method <em>types</em> a parameter when every path that
 * returns from it passes a call that sets a content type on that parameter, and nothing assigns the
 * parameter. After a call to such a method, the response that the caller passes, when it is a bare
 * local variable, has the same content type sites as if the caller had made the calls itself.
 */
final class TypedCalls {
  private TypedCalls() {}

  /** A method with the content type kinds it sets on each argument position. */
  private record Typing(LocalMethod method, Map<Integer, Set<String>> kinds) {}

  /**
   * Returns what adds the content type sites of a typing method after each call to it; the identity
   * when no method sets a content type on a parameter.
   */
  static UnaryOperator<ControlFlowGraph> over(final Map<String, LocalMethod> methods) {
    final List<Typing> typing = new ArrayList<>();
    for (final LocalMethod method : methods.values()) {
      final Map<Integer, Set<String>> kinds = typedParameters(method);
      if (!kinds.isEmpty()) {
        typing.add(new Typing(method, kinds));
      }
    }
    return typing.isEmpty() ? graph -> graph : graph -> apply(graph, typing);
  }

  private static ControlFlowGraph apply(final ControlFlowGraph graph, final List<Typing> typing) {
    final List<BasicBlock> blocks = new ArrayList<>(graph.blocks().size());
    boolean changed = false;
    for (final BasicBlock block : graph.blocks()) {
      final BasicBlock applied = apply(block, typing);
      changed |= applied != block;
      blocks.add(applied);
    }
    return changed
        ? new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit())
        : graph;
  }

  private static BasicBlock apply(final BasicBlock block, final List<Typing> typing) {
    final List<FlowSite> sites = new ArrayList<>(block.sites());
    for (final FlowSite site : block.sites()) {
      if (site.kind() == FlowSiteKind.CALL) {
        typing.stream()
            .filter(candidate -> GuardedCalls.calls(candidate.method(), site))
            .forEach(candidate -> sites.addAll(typed(block, site, candidate)));
      }
    }
    return sites.size() == block.sites().size()
        ? block
        : new BasicBlock(block.id(), block.nodes(), block.successors(), block.accesses(), sites);
  }

  private static List<FlowSite> typed(
      final BasicBlock block,
      final FlowSite call,
      final Typing candidate
  ) {
    final List<FlowSite> added = new ArrayList<>();
    candidate.kinds().forEach(
        (index, kinds) ->
            variable(block, call.arguments().get(index)).ifPresent(
                name ->
                    kinds.forEach(
                        kind ->
                            added.add(
                                new FlowSite(
                                    FlowSiteKind.CONTENT_TYPE,
                                    kind,
                                    name,
                                    Optional.empty(),
                                    List.of(),
                                    List.of(),
                                    call.span()
                                )
                            )
                    )
            )
    );
    return added;
  }

  /** The name of the variable that an argument is, when it is a bare read. */
  private static Optional<String> variable(final BasicBlock block, final Span argument) {
    return block.accesses().stream()
        .filter(access -> access.kind() == AccessKind.READ && access.span().equals(argument))
        .map(VariableAccess::name)
        .findFirst();
  }

  /** The kinds that the method sets on each parameter on every path that returns. */
  static Map<Integer, Set<String>> typedParameters(final LocalMethod method) {
    final Map<Integer, Set<String>> typed = new TreeMap<>();
    final List<String> parameters = method.parameters();
    if (parameters.size() != method.arity()) {
      return typed;
    }
    for (int index = 0; index < parameters.size(); index++) {
      final Set<String> kinds = kinds(method.graph(), parameters.get(index));
      if (!kinds.isEmpty()) {
        typed.put(index, kinds);
      }
    }
    return typed;
  }

  private static Set<String> kinds(final ControlFlowGraph graph, final String parameter) {
    final Set<String> kinds = new TreeSet<>();
    if (assigned(graph, parameter)) {
      return kinds;
    }
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        if (site.kind() == FlowSiteKind.CONTENT_TYPE
            && parameter.equals(site.qualifier())
            && !GuardedCalls.returnsAround(graph, block.id())) {
          kinds.add(site.name());
        }
      }
    }
    return kinds;
  }

  private static boolean assigned(final ControlFlowGraph graph, final String parameter) {
    return graph.blocks().stream()
        .flatMap(block -> block.accesses().stream())
        .anyMatch(access -> access.kind() == AccessKind.WRITE && access.name().equals(parameter));
  }
}
