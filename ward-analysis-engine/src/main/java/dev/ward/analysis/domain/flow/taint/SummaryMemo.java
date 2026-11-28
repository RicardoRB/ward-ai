package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Shares the taint work of a method between the files that reach it. What the analysis of one
 * method gives depends on the method and on the specification entries that match its sites, in
 * order, and on whether a sink entry takes a parameter; nothing else of the file. So two files that
 * reach the same method with the same matching entries get one analysis, and a file whose method or
 * matching entries differ gets its own. One memo serves one run: it holds results, never files.
 */
public final class SummaryMemo {
  /** shortcut: stop adding past this many results; give old entries up when it matters. */
  private static final int LIMIT = 400_000;
  private static final int HASH_STEP = 31;

  private final ConcurrentMap<Key, Core> results;
  private final ConcurrentMap<Identity, GraphFacts> facts = new ConcurrentHashMap<>();

  private SummaryMemo(final ConcurrentMap<Key, Core> results) {
    this.results = results;
  }

  /** A graph compared by identity, as the graphs of a run are lowered once and shared. */
  private record Identity(ControlFlowGraph graph) {
    @Override
    public boolean equals(final Object other) {
      return other instanceof Identity identity && graph == identity.graph;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(graph);
    }
  }

  /** The facts of a graph of another file, read once per run; read again by a disabled memo. */
  GraphFacts facts(final ControlFlowGraph graph) {
    if (results == null) {
      return GraphFacts.of(graph);
    }
    return facts.computeIfAbsent(new Identity(graph), key -> GraphFacts.of(graph));
  }

  /** The hash of a graph: the one read for a graph of the run, or computed for a rewritten one. */
  int hashOf(final ControlFlowGraph graph) {
    final GraphFacts known = results == null ? null : facts.get(new Identity(graph));
    return known == null ? graph.hashCode() : known.hash();
  }

  /**
   * A memo that keeps results.
   *
   * @return an empty memo for the files of one run
   */
  public static SummaryMemo shared() {
    return new SummaryMemo(new ConcurrentHashMap<>());
  }

  /**
   * A memo that keeps nothing, so every method is analysed again.
   *
   * @return a memo that always computes
   */
  public static SummaryMemo disabled() {
    return new SummaryMemo(null);
  }

  /**
   * The number of results kept.
   *
   * @return the size of the memo, zero for a disabled one
   */
  public int size() {
    return results == null ? 0 : results.size();
  }

  Core core(final Key key, final Supplier<Core> compute) {
    if (results == null) {
      return compute.get();
    }
    final Core known = results.get(key);
    if (known != null) {
      return known;
    }
    final Core fresh = compute.get();
    if (results.size() < LIMIT) {
      results.putIfAbsent(key, fresh);
    }
    return fresh;
  }

  /** The part of a method summary that the analysis of the method decides. */
  record Core(Optional<Taint> returned, Map<Integer, List<String>> sinkFlows, boolean clean) {
    Core {
      sinkFlows = Collections.unmodifiableSortedMap(new TreeMap<>(sinkFlows));
    }
  }

  /** A method with its hash read once, as one file asks for it in every round. */
  static final class Print {
    private final LocalMethod method;
    private final int hash;

    Print(final LocalMethod method, final int graphHash) {
      this.method = method;
      this.hash =
          HASH_STEP * Objects.hash(
              method.name(),
              method.owner(),
              method.arity(),
              method.external(),
              method.bare()
          ) + graphHash;
    }

    @Override
    public boolean equals(final Object other) {
      return other instanceof Print print
          && hash == print.hash
          && method.name().equals(print.method.name())
          && method.owner().equals(print.method.owner())
          && method.arity() == print.method.arity()
          && method.external() == print.method.external()
          && method.bare() == print.method.bare()
          && (method.graph() == print.method.graph()
              || method.graph().equals(print.method.graph()));
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  /** What decides the analysis of a method: the method and what the specification says of it. */
  record Key(Print method, Matched matched) {}

  /** The entries that match a site of a method, per kind of entry, in specification order. */
  record Matched(
      List<TaintEntry> sources,
      List<TaintEntry> sinks,
      List<TaintEntry> sanitizers,
      List<TaintEntry> propagators,
      boolean parameterSink
  ) {}
}
