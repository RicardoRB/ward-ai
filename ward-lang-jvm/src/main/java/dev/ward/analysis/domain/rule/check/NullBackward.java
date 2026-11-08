package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The backward half of the null data-flow: at each point, the variables that every path from there
 * dereferences, or passes to a parameter, returns or stores where a value that is not null is
 * required, before they are written again, with the places where that first happens. Paths that end
 * in a {@code throw} and the edges that statements take when they throw do not count against it.
 */
final class NullBackward {

  /** Why a place needs a value that is not null. */
  enum Use {
    /** A dereference. */
    PLAIN,
    /** An argument for a parameter that must not be null. */
    PARAMETER,
    /** A return from a method that must not return null. */
    RETURN,
    /** A store into a field that must not be null. */
    FIELD
  }

  /**
   * A place that needs a value that is not null.
   *
   * @param at the expression
   * @param use why it needs it
   * @param block the block it is in
   */
  record Site(Span at, Use use, int block) {}

  /** Tells which uses of a value need it to be not null. */
  interface Needs {
    /** Returns why argument {@code index} of {@code call} needs a value, or null if it does not. */
    Use argument(NullEvent call, int index);

    /** Tells whether the method requires the value it returns to be not null. */
    boolean returns();

    /** Tells whether the field requires the value stored into it to be not null. */
    boolean field(String name);
  }

  /**
   * What is dereferenced on every path from a point.
   *
   * @param everything whether the point has no path to the end, so any variable counts
   * @param sites the places per variable
   */
  record Fact(boolean everything, Map<Span, Set<Site>> sites) {
    static final Fact TOP = new Fact(true, Map.of());
    static final Fact NONE = new Fact(false, Map.of());

    Fact {
      sites = Map.copyOf(sites);
    }

    /** Tells whether the variable is dereferenced on every path. */
    boolean has(final Span variable) {
      return sites.containsKey(variable);
    }
  }

  private final NullGraph graph;
  private final Needs needs;
  private final Map<Integer, Fact> in = new HashMap<>();
  private final Map<Integer, List<Fact>> points = new HashMap<>();

  NullBackward(final NullGraph graph, final Needs needs) {
    this.graph = graph;
    this.needs = needs;
    solve();
  }

  /** The fact at the start of {@code block}. */
  Fact in(final int block) {
    return in.getOrDefault(block, Fact.TOP);
  }

  /** The fact before event {@code index} of {@code block}; the last index is the end. */
  Fact before(final int block, final int index) {
    return points.get(block).get(index);
  }

  private void solve() {
    final List<BasicBlock> blocks = graph.graph().blocks();
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int index = blocks.size() - 1; index >= 0; index--) {
        final BasicBlock block = blocks.get(index);
        final Fact start = walk(block, out(block));
        if (!start.equals(in.get(block.id()))) {
          in.put(block.id(), start);
          changed = true;
        }
      }
    }
    for (final BasicBlock block : blocks) {
      walk(block, out(block));
    }
  }

  private Fact out(final BasicBlock block) {
    Fact met = null;
    for (final FlowEdge edge : block.successors()) {
      if (edge.kind() != FlowEdgeKind.EXCEPTION) {
        met = met == null ? in(edge.target()) : meet(met, in(edge.target()));
      }
    }
    return met == null ? ending(block) : met;
  }

  private static Fact ending(final BasicBlock block) {
    final boolean throwing =
        !block.nodes().isEmpty()
            && SyntaxWalk.text(block.nodes().get(block.nodes().size() - 1)).startsWith("throw");
    return throwing ? Fact.TOP : Fact.NONE;
  }

  private static Fact meet(final Fact left, final Fact right) {
    if (left.everything()) {
      return right;
    }
    if (right.everything()) {
      return left;
    }
    final Map<Span, Set<Site>> met = new HashMap<>();
    left.sites()
        .forEach(
            (variable, sites) -> {
              if (right.sites().containsKey(variable)) {
                final Set<Site> union = new HashSet<>(sites);
                union.addAll(right.sites().get(variable));
                met.put(variable, union);
              }
            });
    return new Fact(false, met);
  }

  private Fact walk(final BasicBlock block, final Fact end) {
    final List<NullEvent> events = graph.events().get(block.id());
    final Fact[] facts = new Fact[events.size() + 1];
    facts[events.size()] = end;
    for (int index = events.size() - 1; index >= 0; index--) {
      facts[index] = step(facts[index + 1], events.get(index), block.id());
    }
    points.put(block.id(), List.of(facts));
    return facts[0];
  }

  private Fact step(final Fact after, final NullEvent event, final int block) {
    if (after.everything()) {
      return after;
    }
    final Map<Span, Set<Site>> sites = new HashMap<>(after.sites());
    switch (event.kind()) {
      case DEREF ->
          sites.put(event.variable(), Set.of(new Site(event.node().span(), Use.PLAIN, block)));
      case CALL -> arguments(sites, event, block);
      case RETURN ->
          required(sites, event.operand(), event, needs.returns() ? Use.RETURN : null, block);
      case STORE ->
          required(
              sites, event.operand(), event, needs.field(event.name()) ? Use.FIELD : null, block);
      case WRITE, KILL -> sites.remove(event.variable());
      default -> {
        // A comparison does not dereference.
      }
    }
    return new Fact(false, sites);
  }

  private void arguments(final Map<Span, Set<Site>> sites, final NullEvent call, final int block) {
    final List<NullEvent.Arg> args = call.args();
    for (int index = 0; index < args.size(); index++) {
      final NullEvent.Arg arg = args.get(index);
      final Use use = needs.argument(call, index);
      if (use != null && arg.operand().variable() != null) {
        sites.put(arg.operand().variable(), site(arg.node().span(), use, block));
      }
    }
  }

  private static void required(
      final Map<Span, Set<Site>> sites,
      final NullEvent.Operand operand,
      final NullEvent event,
      final Use use,
      final int block) {
    if (use != null && operand.variable() != null) {
      sites.put(operand.variable(), site(event.node().span(), use, block));
    }
  }

  private static Set<Site> site(final Span at, final Use use, final int block) {
    return Set.of(new Site(at, use, block));
  }

  /** Returns the block ids in the order the facts were computed. */
  List<Integer> blocks() {
    return new ArrayList<>(points.keySet());
  }
}
