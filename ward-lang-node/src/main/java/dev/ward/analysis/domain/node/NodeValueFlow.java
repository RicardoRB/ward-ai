package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Follows a name to the assignments that can supply its value: the declaration with an
 * initializer, an assignment, a compound assignment and the value the name held before it. A name
 * that a parameter, a loop binding or a destructuring writes has no assignment this reads.
 */
final class NodeValueFlow {
  private static final int BINARY_PARTS = 3;
  private static final int VALUE_INDEX = 2;
  private static final String PLUS_ASSIGN = "+=";
  private static final String ASSIGN = "=";
  private static final String DECLARATION = "variableDeclaration";

  private final List<ControlFlowGraph> graphs;
  private final Map<ControlFlowGraph, ReachingDefinitions> reaching = new HashMap<>();

  /** A test of one expression, which the assignments of a name are tested with too. */
  @FunctionalInterface
  interface ValueTest {
    boolean holds(SyntaxNode expression, Set<AccessSite> visiting, int depth);
  }

  NodeValueFlow(final List<ControlFlowGraph> graphs) {
    this.graphs = List.copyOf(graphs);
  }

  /**
   * Whether every assignment that can supply the value of a read passes the test.
   *
   * @param read the expression that reads the name
   * @param visiting the assignments being tested, which a loop reaches again
   * @param depth how deep the analysis is
   * @param test the test of the assigned value
   * @param numbers whether a compound assignment of arithmetic always passes
   */
  boolean allAssigned(
      final SyntaxNode read,
      final Set<AccessSite> visiting,
      final int depth,
      final ValueTest test,
      final boolean numbers
  ) {
    final Optional<ControlFlowGraph> graph = graphOf(read.span());
    if (graph.isEmpty()) {
      return false;
    }
    final ReachingDefinitions definitions =
        reaching.computeIfAbsent(graph.get(), ReachingDefinitions::of);
    final Optional<AccessSite> use = readAt(definitions, read.span());
    final Context context = new Context(graph.get(), definitions, visiting, test, numbers);
    return use.isPresent()
        && !definitions.reaching(use.get()).isEmpty()
        && definitions.reaching(use.get()).stream()
            .allMatch(definition -> holds(context, definition, depth));
  }

  /** What one walk over the assignments carries. */
  private record Context(
      ControlFlowGraph graph,
      ReachingDefinitions definitions,
      Set<AccessSite> visiting,
      ValueTest test,
      boolean numbers
  ) {}

  private boolean holds(final Context context, final AccessSite definition, final int depth) {
    if (definition.access().kind() != AccessKind.WRITE) {
      return false;
    }
    if (!context.visiting().add(definition)) {
      return true;
    }
    final boolean result = writeHolds(context, definition, depth);
    context.visiting().remove(definition);
    return result;
  }

  private boolean writeHolds(final Context context, final AccessSite definition, final int depth) {
    final BasicBlock block = context.graph().blocks().get(definition.block());
    final Span name = definition.access().span();
    for (final SyntaxNode statement : block.nodes()) {
      if (contains(statement.span(), name)) {
        final Optional<Assigned> assigned = assigned(statement, definition.access().name(), name);
        return assigned.isPresent() && valueHolds(context, definition, assigned.get(), depth);
      }
    }
    return false;
  }

  private boolean valueHolds(
      final Context context,
      final AccessSite definition,
      final Assigned assigned,
      final int depth
  ) {
    if (context.numbers() && NodeNumericShapes.assignment(assigned.operator())) {
      return true;
    }
    if (!context.test().holds(assigned.value(), context.visiting(), depth + 1)) {
      return false;
    }
    if (!PLUS_ASSIGN.equals(assigned.operator())) {
      return ASSIGN.equals(assigned.operator());
    }
    return readAt(context.definitions(), definition.access().span())
        .map(
            previous ->
                context.definitions().reaching(previous).stream()
                    .allMatch(earlier -> holds(context, earlier, depth + 1))
        )
        .orElse(false);
  }

  private static Optional<AccessSite> readAt(
      final ReachingDefinitions definitions,
      final Span span
  ) {
    return definitions.uses().stream()
        .filter(site -> site.access().kind() == AccessKind.READ)
        .filter(site -> site.access().span().startOffset() == span.startOffset())
        .findFirst();
  }

  /** The value written to a name by a declaration or an assignment inside a statement. */
  private static Optional<Assigned> assigned(
      final SyntaxNode statement,
      final String name,
      final Span at
  ) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(statement);
    if (DECLARATION.equals(statement.kind())
        && parts.size() >= BINARY_PARTS
        && name.equals(NodeFlowSyntax.text(parts.get(0)))
        && ASSIGN.equals(NodeFlowSyntax.text(parts.get(parts.size() - 2)))) {
      return Optional.of(new Assigned(ASSIGN, parts.get(parts.size() - 1)));
    }
    if (NodeTemplates.SINGLE.equals(statement.kind())
        && parts.size() == BINARY_PARTS
        && name.equals(NodeFlowSyntax.text(parts.get(0)))
        && parts.get(0).span().startOffset() == at.startOffset()) {
      return Optional.of(new Assigned(NodeFlowSyntax.text(parts.get(1)), parts.get(VALUE_INDEX)));
    }
    return statement.children().stream()
        .filter(child -> contains(child.span(), at))
        .map(child -> assigned(child, name, at))
        .flatMap(Optional::stream)
        .findFirst();
  }

  /** The innermost graph whose body holds the span. */
  private Optional<ControlFlowGraph> graphOf(final Span span) {
    ControlFlowGraph best = null;
    for (final ControlFlowGraph graph : graphs) {
      if (contains(graph.span(), span) && (best == null || contains(best.span(), graph.span()))) {
        best = graph;
      }
    }
    return Optional.ofNullable(best);
  }

  private static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  private record Assigned(String operator, SyntaxNode value) {}
}
