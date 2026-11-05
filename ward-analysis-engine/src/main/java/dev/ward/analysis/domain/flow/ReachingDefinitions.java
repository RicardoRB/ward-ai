package dev.ward.analysis.domain.flow;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reaching definitions of one graph: for each read of a variable, the writes to that variable that
 * can reach it along some path, through normal, branch and exception edges. A write is a {@link
 * AccessKind#WRITE} or a {@link AccessKind#PARAMETER}. A read is a {@link AccessKind#READ} or a
 * {@link AccessKind#CAPTURE}.
 */
public final class ReachingDefinitions {

  private final List<AccessSite> definitions;
  private final List<AccessSite> uses;
  private final Map<AccessSite, List<AccessSite>> reaching;
  private final Set<AccessSite> overwritten;

  private ReachingDefinitions(
      final List<AccessSite> definitions,
      final List<AccessSite> uses,
      final Map<AccessSite, List<AccessSite>> reaching,
      final Set<AccessSite> overwritten) {
    this.definitions = definitions;
    this.uses = uses;
    this.reaching = reaching;
    this.overwritten = overwritten;
  }

  /**
   * Computes the reaching definitions of a graph. The result depends only on the graph, and the
   * definitions and uses are listed in block order, then in access order.
   *
   * @param graph the graph to analyse
   * @return the definitions, the uses and the definitions that reach each use
   */
  public static ReachingDefinitions of(final ControlFlowGraph graph) {
    final Writes writes = Writes.of(graph);
    final BitSet[] in = FixedPointStates.entryStates(graph, writes);
    final Sweep sweep = new Sweep();
    for (final BasicBlock block : graph.blocks()) {
      sweep.block(block, in[block.id()], writes);
    }
    return new ReachingDefinitions(
        writes.ordered(), List.copyOf(sweep.uses), sweep.reaching, Set.copyOf(sweep.overwritten));
  }

  /** Returns the writes of the graph, in block order. */
  public List<AccessSite> definitions() {
    return List.copyOf(definitions);
  }

  /** Returns the reads of the graph, in block order: each read and each capture. */
  public List<AccessSite> uses() {
    return List.copyOf(uses);
  }

  /**
   * Returns the writes that can reach an access: for a read, the writes whose value it may see; for
   * a write, the writes that are still live when it runs.
   *
   * @param site a read, capture or write of the graph
   * @return the reaching writes, in block order; empty when the site is not an access of the graph
   */
  public List<AccessSite> reaching(final AccessSite site) {
    return reaching.getOrDefault(site, List.of());
  }

  /**
   * Reports whether a different write of the same variable can follow a write on some path, so the
   * value of the write may be replaced before it is read. A write that reaches itself through a
   * loop is not overwritten by that rule.
   *
   * @param definition a write of the graph
   * @return true when another write of the variable is reached by this write
   */
  public boolean overwritten(final AccessSite definition) {
    return overwritten.contains(definition);
  }

  /** The uses, their reaching writes and the overwritten writes, collected block by block. */
  private static final class Sweep {

    private final List<AccessSite> uses = new ArrayList<>();
    private final Map<AccessSite, List<AccessSite>> reaching = new HashMap<>();
    private final Set<AccessSite> overwritten = new HashSet<>();

    void block(final BasicBlock block, final BitSet entry, final Writes writes) {
      final BitSet current = (BitSet) entry.clone();
      for (final AccessSite site : sites(block)) {
        if (isWrite(site.access())) {
          write(site, current, writes);
        } else if (isRead(site.access())) {
          uses.add(site);
          reaching.put(site, writes.reaching(current, site));
        }
      }
    }

    private void write(final AccessSite site, final BitSet current, final Writes writes) {
      final List<AccessSite> before = writes.reaching(current, site);
      reaching.put(site, before);
      before.stream().filter(earlier -> !earlier.equals(site)).forEach(overwritten::add);
      writes.apply(current, site);
    }
  }

  static List<AccessSite> sites(final BasicBlock block) {
    final List<AccessSite> found = new ArrayList<>();
    final List<VariableAccess> accesses = block.accesses();
    for (int index = 0; index < accesses.size(); index++) {
      found.add(new AccessSite(block.id(), index, accesses.get(index)));
    }
    return found;
  }

  static boolean isWrite(final VariableAccess access) {
    return access.kind() == AccessKind.WRITE || access.kind() == AccessKind.PARAMETER;
  }

  private static boolean isRead(final VariableAccess access) {
    return access.kind() == AccessKind.READ || access.kind() == AccessKind.CAPTURE;
  }

  /** The writes of one graph, numbered in block order, with the writes of each variable. */
  static final class Writes {

    private final List<AccessSite> sites = new ArrayList<>();
    private final Map<VariableKey, BitSet> ofVariable = new HashMap<>();
    private final Map<AccessSite, Integer> numbers = new HashMap<>();

    static Writes of(final ControlFlowGraph graph) {
      final Writes writes = new Writes();
      for (final BasicBlock block : graph.blocks()) {
        for (final AccessSite site : sites(block)) {
          if (isWrite(site.access())) {
            writes.add(site);
          }
        }
      }
      return writes;
    }

    private void add(final AccessSite site) {
      final int number = sites.size();
      sites.add(site);
      numbers.put(site, number);
      ofVariable.computeIfAbsent(VariableKey.of(site.access()), key -> new BitSet()).set(number);
    }

    /** Kills the other writes of the variable and makes this write the one that reaches on. */
    void apply(final BitSet current, final AccessSite site) {
      current.andNot(ofVariable.get(VariableKey.of(site.access())));
      current.set(numbers.get(site));
    }

    /** Returns the live writes of the access's own variable, in block order. */
    List<AccessSite> reaching(final BitSet current, final AccessSite site) {
      final BitSet writesOfVariable = ofVariable.get(VariableKey.of(site.access()));
      if (writesOfVariable == null) {
        return List.of();
      }
      final BitSet same = (BitSet) current.clone();
      same.and(writesOfVariable);
      final List<AccessSite> found = new ArrayList<>();
      for (int number = same.nextSetBit(0); number >= 0; number = same.nextSetBit(number + 1)) {
        found.add(sites.get(number));
      }
      return List.copyOf(found);
    }

    List<AccessSite> ordered() {
      return List.copyOf(sites);
    }
  }
}
