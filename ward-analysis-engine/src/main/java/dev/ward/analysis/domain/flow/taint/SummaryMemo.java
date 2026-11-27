package dev.ward.analysis.domain.flow.taint;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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

  private final ConcurrentMap<Key, Core> results;

  private SummaryMemo(final ConcurrentMap<Key, Core> results) {
    this.results = results;
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

    Print(final LocalMethod method) {
      this.method = method;
      this.hash = method.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
      return other instanceof Print print && hash == print.hash && method.equals(print.method);
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
