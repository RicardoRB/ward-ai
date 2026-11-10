package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.python.PythonLoops;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a name read in a body before a binding reaches the read, like Pylint {@code E0601},
 * {@code E0606} and {@code W0631}. A binding reaches a read along every path (must) or along some
 * path (may). A read with no reaching binding is {@link Problem#NEVER_BOUND}. A read that some path
 * leaves unbound is {@link Problem#POSSIBLY_UNBOUND}, unless every binding on the other paths is
 * the target of a loop that may run zero times, which is {@link Problem#LOOP_VARIABLE}. Only the
 * names a body binds itself are checked, so a global or a builtin is never reported.
 */
public final class UsedBeforeAssignmentCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** No path binds the name before the read. */
    NEVER_BOUND,
    /** Some path reaches the read without binding the name, and another binds it. */
    POSSIBLY_UNBOUND,
    /** The name is bound only as the target of a loop that may run zero times. */
    LOOP_VARIABLE
  }

  private static final String NAME = "{value}";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public UsedBeforeAssignmentCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the control-flow graphs, which only {@link #matches(SyntaxNode,
   * FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the used-before-assignment check needs the control flow");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final Set<Span> loopTargets = new HashSet<>(PythonLoops.unsafeTargets(root));
    final List<RuleMatch> found = new ArrayList<>();
    for (final ControlFlowGraph graph : flow.graphs()) {
      found.addAll(unbound(graph, loopTargets));
    }
    found.sort(Comparator.comparingInt(match -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private List<RuleMatch> unbound(final ControlFlowGraph graph, final Set<Span> loopTargets) {
    final Set<String> locals = localNames(graph);
    final List<State> entry = Bindings.solve(graph, loopTargets);
    final List<RuleMatch> found = new ArrayList<>();
    for (final BasicBlock block : graph.blocks()) {
      final State state = entry.get(block.id());
      if (state != null) {
        found.addAll(reads(block, state, locals, loopTargets));
      }
    }
    return found;
  }

  /** Walks one block from its entry state, reporting the reads the state does not cover. */
  private List<RuleMatch> reads(
      final BasicBlock block,
      final State start,
      final Set<String> locals,
      final Set<Span> loopTargets) {
    final State state = start.copy();
    final List<RuleMatch> found = new ArrayList<>();
    for (final VariableAccess access : block.accesses()) {
      if (access.kind() == AccessKind.READ
          && locals.contains(access.name())
          && state.problemOf(access.name()) == problem) {
        found.add(new RuleMatch(access.span(), message.replace(NAME, access.name())));
      }
      state.apply(access, loopTargets);
    }
    return found;
  }

  /** The names the body binds in some block, which are its locals. */
  private static Set<String> localNames(final ControlFlowGraph graph) {
    final Set<String> locals = new HashSet<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final VariableAccess access : block.accesses()) {
        if (access.kind() != AccessKind.READ && access.kind() != AccessKind.CAPTURE) {
          locals.add(access.name());
        }
      }
    }
    return locals;
  }

  /**
   * The names a path binds on every path (must), on some path (may), and on some path by a binding
   * that is not a loop target (plain).
   */
  static final class State {

    private final Set<String> must;
    private final Set<String> may;
    private final Set<String> plain;

    State(final Set<String> must, final Set<String> may, final Set<String> plain) {
      this.must = new HashSet<>(must);
      this.may = new HashSet<>(may);
      this.plain = new HashSet<>(plain);
    }

    State copy() {
      return new State(must, may, plain);
    }

    /** The mistake of a read of {@code name} in this state, or null when a binding reaches it. */
    Problem problemOf(final String name) {
      if (!may.contains(name)) {
        return Problem.NEVER_BOUND;
      }
      if (must.contains(name)) {
        return null;
      }
      return plain.contains(name) ? Problem.POSSIBLY_UNBOUND : Problem.LOOP_VARIABLE;
    }

    void apply(final VariableAccess access, final Set<Span> loopTargets) {
      final String name = access.name();
      switch (access.kind()) {
        case WRITE, PARAMETER -> {
          must.add(name);
          may.add(name);
          if (loopTargets.contains(access.span())) {
            plain.remove(name);
          } else {
            plain.add(name);
          }
        }
        case DECLARE -> {
          must.remove(name);
          may.remove(name);
          plain.remove(name);
        }
        default -> {
          // A read or a capture binds nothing.
        }
      }
    }

    Set<String> must() {
      return must;
    }

    Set<String> may() {
      return may;
    }

    Set<String> plain() {
      return plain;
    }
  }

  /** The fixed point of the binding states over the blocks of one graph. */
  private static final class Bindings {

    /** Returns the entry state of each block, or null for a block no path reaches. */
    static List<State> solve(final ControlFlowGraph graph, final Set<Span> loopTargets) {
      final List<State> entry = new ArrayList<>();
      for (int index = 0; index < graph.blocks().size(); index++) {
        entry.add(null);
      }
      entry.set(graph.entry(), new State(Set.of(), Set.of(), Set.of()));
      boolean changed = true;
      while (changed) {
        changed = false;
        for (final BasicBlock block : graph.blocks()) {
          final State start = entry.get(block.id());
          if (start != null) {
            changed |= propagate(block, start, entry, loopTargets);
          }
        }
      }
      return entry;
    }

    /** Pushes a block's exit state to each successor, reporting whether a successor changed. */
    private static boolean propagate(
        final BasicBlock block,
        final State start,
        final List<State> entry,
        final Set<Span> loopTargets) {
      final State exit = start.copy();
      for (final VariableAccess access : block.accesses()) {
        exit.apply(access, loopTargets);
      }
      boolean changed = false;
      for (final FlowEdge edge : block.successors()) {
        changed |= join(entry, edge.target(), exit);
      }
      return changed;
    }

    /** Joins an exit state into a successor's entry state: intersect the must, union the rest. */
    private static boolean join(final List<State> entry, final int target, final State exit) {
      final State current = entry.get(target);
      if (current == null) {
        entry.set(target, exit.copy());
        return true;
      }
      final Set<String> must = new HashSet<>(current.must());
      must.retainAll(exit.must());
      final Set<String> may = new HashSet<>(current.may());
      may.addAll(exit.may());
      final Set<String> plain = new HashSet<>(current.plain());
      plain.addAll(exit.plain());
      if (must.equals(current.must())
          && may.equals(current.may())
          && plain.equals(current.plain())) {
        return false;
      }
      entry.set(target, new State(must, may, plain));
      return true;
    }
  }
}
