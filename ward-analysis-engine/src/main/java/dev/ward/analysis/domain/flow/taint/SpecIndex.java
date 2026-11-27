package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A specification arranged to find the entries that match the sites of a graph without testing
 * every entry on every site. An entry whose name is exact is found by its kind and name; the others
 * are tested on each site of their kind.
 */
final class SpecIndex {
  private final Entries sources;
  private final Entries sinks;
  private final Entries sanitizers;
  private final Entries propagators;
  private final boolean parameterSink;

  SpecIndex(final TaintSpec spec) {
    this.sources = new Entries(spec.sources());
    this.sinks = new Entries(spec.sinks());
    this.sanitizers = new Entries(spec.sanitizers());
    this.propagators = new Entries(spec.propagators());
    this.parameterSink =
        spec.sinks().stream().anyMatch(entry -> entry.kind() == FlowSiteKind.PARAMETER);
  }

  /** The entries of each kind that match a site of the graph, in specification order. */
  SummaryMemo.Matched matched(final ControlFlowGraph graph) {
    final SortedSet<Integer> source = new TreeSet<>();
    final SortedSet<Integer> sink = new TreeSet<>();
    final SortedSet<Integer> sanitizer = new TreeSet<>();
    final SortedSet<Integer> propagator = new TreeSet<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        final List<String> chain = Entries.chainOf(site);
        sources.collect(site, chain, source);
        sinks.collect(site, chain, sink);
        sanitizers.collect(site, chain, sanitizer);
        propagators.collect(site, chain, propagator);
      }
    }
    return new SummaryMemo.Matched(
        sources.at(source),
        sinks.at(sink),
        sanitizers.at(sanitizer),
        propagators.at(propagator),
        parameterSink
    );
  }

  private static final class Entries {
    private static final String UNQUALIFIED = "<none>";
    private static final Pattern CHAIN = Pattern.compile("\\??\\.");

    private final List<TaintEntry> all;
    private final Map<String, List<Integer>> exact = new HashMap<>();
    private final Map<String, List<Integer>> qualified = new HashMap<>();
    private final Map<FlowSiteKind, List<Integer>> others = new HashMap<>();

    Entries(final List<TaintEntry> entries) {
      this.all = entries;
      for (int index = 0; index < entries.size(); index++) {
        final TaintEntry entry = entries.get(index);
        if (!isExact(entry)) {
          add(others, entry.kind(), index);
        } else if (isQualified(entry)) {
          add(qualified, slot(entry), index);
        } else {
          add(exact, slot(entry.kind(), entry.name()), index);
        }
      }
    }

    /** An entry that a site matches by name, so a lookup by kind and name finds it. */
    private static <K> void add(final Map<K, List<Integer>> map, final K key, final int index) {
      List<Integer> list = map.get(key);
      if (list == null) {
        list = new ArrayList<>();
        map.put(key, list);
      }
      list.add(index);
    }

    private static boolean isExact(final TaintEntry entry) {
      return entry.kind() != FlowSiteKind.STRING_BUILD
          && entry.kind() != FlowSiteKind.LITERAL
          && entry.kind() != FlowSiteKind.PARAMETER
          && !entry.name().endsWith("*");
    }

    /** An entry with a plain qualifier and no receiver: a site matches it by one chain member. */
    private static boolean isQualified(final TaintEntry entry) {
      return entry.receiver().isEmpty()
          && entry.qualifier().isPresent()
          && !entry.qualifier().get().startsWith("*")
          && !UNQUALIFIED.equals(entry.qualifier().get());
    }

    private static String slot(final TaintEntry entry) {
      return slot(entry.kind(), entry.name()) + ":" + entry.qualifier().orElseThrow();
    }

    private static String slot(final FlowSiteKind kind, final String name) {
      return kind.ordinal() + ":" + name;
    }

    void collect(final FlowSite site, final List<String> chain, final SortedSet<Integer> into) {
      final String named = slot(site.kind(), site.name());
      test(exact.get(named), site, into);
      if (!qualified.isEmpty()) {
        test(qualified.get(named + ":" + site.qualifier()), site, into);
        for (final String member : chain) {
          test(qualified.get(named + ":" + member), site, into);
        }
      }
      test(others.get(site.kind()), site, into);
    }

    /** The members of a qualifier chain, as an entry with a plain qualifier sees them. */
    static List<String> chainOf(final FlowSite site) {
      return Arrays.asList(CHAIN.split(site.qualifier()));
    }

    private void test(
        final List<Integer> candidates,
        final FlowSite site,
        final SortedSet<Integer> into
    ) {
      if (candidates == null) {
        return;
      }
      for (final int index : candidates) {
        if (!into.contains(index) && all.get(index).matches(site)) {
          into.add(index);
        }
      }
    }

    List<TaintEntry> at(final SortedSet<Integer> indexes) {
      final List<TaintEntry> found = new ArrayList<>(indexes.size());
      for (final int index : indexes) {
        found.add(all.get(index));
      }
      return found;
    }
  }
}
