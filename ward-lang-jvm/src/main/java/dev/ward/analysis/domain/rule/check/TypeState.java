package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A forward data-flow analysis over the control-flow graph of one body: for each local variable,
 * the type of the value last assigned to it, when every path to the point agrees. Parameters,
 * captured variables, compound assignments and values of unknown type leave a variable unknown. A
 * rule that reads the state sees what the variable holds, which can be narrower than its declared
 * type.
 */
final class TypeState {

  private static final String EXPRESSION = "expression";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String INITIALIZER = "variableInitializer";
  private static final String NEW = "'new'";
  private static final int ASSIGNMENT_PARTS = 3;

  private final UnitTypeFacts types;

  /**
   * The type of a value held by a variable.
   *
   * @param type the static type of the value assigned
   * @param ancestors the names of the supertypes above the type, when they are known
   * @param ancestorsKnown whether {@code ancestors} is complete
   * @param exact whether the value is of exactly this type: made by {@code new}, or a final type
   */
  record Known(TypeRef type, Set<String> ancestors, boolean ancestorsKnown, boolean exact) {}

  /** Receives each node of a block with the state before it. */
  @FunctionalInterface
  interface Visitor {

    /** Visits {@code node}, which lies in {@code block}, with the variables' state before it. */
    void visit(SyntaxNode node, BasicBlock block, Map<Span, Known> state);
  }

  TypeState(final UnitTypeFacts types) {
    this.types = types;
  }

  /** Visits every node of every block reachable from the entry, in block order. */
  void walk(final ControlFlowGraph graph, final Visitor visitor) {
    final Map<Integer, Map<Span, Known>> entry = fixpoint(graph);
    for (final BasicBlock block : graph.blocks()) {
      final Map<Span, Known> before = entry.get(block.id());
      if (before == null) {
        continue;
      }
      final Map<Span, Known> state = mutable(before);
      for (final SyntaxNode node : block.nodes()) {
        visitor.visit(node, block, Map.copyOf(state));
        apply(block, node, state);
      }
    }
  }

  private Map<Integer, Map<Span, Known>> fixpoint(final ControlFlowGraph graph) {
    final Map<Integer, Map<Span, Known>> entry = new HashMap<>();
    entry.put(graph.entry(), Map.of());
    final Deque<Integer> pending = new ArrayDeque<>(List.of(graph.entry()));
    while (!pending.isEmpty()) {
      final BasicBlock block = graph.blocks().get(pending.poll());
      final Map<Span, Known> state = mutable(entry.get(block.id()));
      block.nodes().forEach(node -> apply(block, node, state));
      for (final FlowEdge edge : block.successors()) {
        final Map<Span, Known> known = entry.get(edge.target());
        final Map<Span, Known> merged = known == null ? state : meet(known, state);
        if (!merged.equals(known)) {
          entry.put(edge.target(), merged);
          pending.add(edge.target());
        }
      }
    }
    return entry;
  }

  private static Map<Span, Known> mutable(final Map<Span, Known> state) {
    return new HashMap<>(state);
  }

  /** Keeps the variables that both states hold with the same type. */
  private static Map<Span, Known> meet(final Map<Span, Known> left, final Map<Span, Known> right) {
    return left.entrySet().stream()
        .filter(entry -> entry.getValue().equals(right.get(entry.getKey())))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /** Applies the writes of {@code node} to {@code state}. */
  private void apply(final BasicBlock block, final SyntaxNode node, final Map<Span, Known> state) {
    for (final VariableAccess access : block.accesses()) {
      if (!inside(node.span(), access.span())) {
        continue;
      }
      if (access.kind() == AccessKind.WRITE) {
        final Optional<Known> known = valueOf(node, access).flatMap(this::known);
        if (known.isPresent()) {
          state.put(access.declaration(), known.get());
        } else {
          state.remove(access.declaration());
        }
      } else if (access.kind() == AccessKind.PARAMETER || access.kind() == AccessKind.CAPTURE) {
        state.remove(access.declaration());
      }
    }
  }

  private static boolean inside(final Span outer, final Span inner) {
    return inner.startOffset() >= outer.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  /** Returns the expression assigned to the variable by a simple declaration or assignment. */
  private static Optional<SyntaxNode> valueOf(final SyntaxNode node, final VariableAccess write) {
    final Set<SyntaxNode> all = new HashSet<>(SyntaxWalk.descendants(node));
    all.add(node);
    for (final SyntaxNode candidate : all) {
      if (DECLARATOR.equals(candidate.kind()) && declares(candidate, write)) {
        return candidate.children().stream()
            .filter(child -> INITIALIZER.equals(child.kind()))
            .findFirst()
            .flatMap(TypeState::expressionOf);
      }
      if (Assignments.isAssignment(candidate)
          && candidate.children().size() == ASSIGNMENT_PARTS
          && "'='".equals(candidate.children().get(1).kind())
          && candidate.children().get(0).span().equals(write.span())) {
        return Optional.of(candidate.children().get(2));
      }
    }
    return Optional.empty();
  }

  private static boolean declares(final SyntaxNode declarator, final VariableAccess write) {
    return declarator.children().stream()
        .anyMatch(
            child ->
                "variableDeclaratorId".equals(child.kind()) && child.span().equals(write.span()));
  }

  private static Optional<SyntaxNode> expressionOf(final SyntaxNode initializer) {
    return initializer.children().stream()
        .filter(child -> EXPRESSION.equals(child.kind()))
        .findFirst();
  }

  /** Returns what the facts say about the value of {@code expression}, if its type is known. */
  private Optional<Known> known(final SyntaxNode expression) {
    final Optional<TypeRef> type =
        types
            .typeOf(expression.span())
            .filter(known -> !known.primitive() || known.arrayDepth() > 0);
    if (type.isEmpty()) {
      return Optional.empty();
    }
    final Optional<TypeShape> shape = types.shapeOf(expression.span());
    final Set<String> ancestors =
        types
            .membersOf(expression.span())
            .map(m -> m.ancestors().stream().map(TypeRef::name).collect(Collectors.toSet()))
            .orElse(Set.of());
    final boolean created =
        !expression.children().isEmpty() && NEW.equals(expression.children().get(0).kind());
    final boolean finalType =
        shape
            .filter(
                s ->
                    s.isFinal()
                        || s.kind() == TypeShape.Kind.ENUM
                        || s.kind() == TypeShape.Kind.RECORD)
            .isPresent();
    return Optional.of(
        new Known(type.get(), ancestors, !ancestors.isEmpty(), created || finalType));
  }
}
