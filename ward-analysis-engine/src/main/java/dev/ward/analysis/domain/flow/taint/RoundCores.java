package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the analysis of each method gave in the rounds of one file. The entries that a round adds to
 * the specification are named after the methods whose summary changed, so a method whose sites name
 * none of them matches the same entries as before and keeps its result, with no lookup.
 */
final class RoundCores {
  private final Map<String, SummaryMemo.Core> cores = new HashMap<>();
  private final Map<String, Set<String>> names = new HashMap<>();
  private Set<String> changed;

  /** Tells which names the last round changed; before the first call every method is analysed. */
  void changed(final Set<String> now) {
    this.changed = now;
  }

  /** The result of an earlier round for a method that no changed name reaches. */
  Optional<SummaryMemo.Core> reuse(final LocalMethod method) {
    final SummaryMemo.Core known = changed == null ? null : cores.get(method.key());
    if (known == null || reaches(method)) {
      return Optional.empty();
    }
    return Optional.of(known);
  }

  /** Keeps the result of a method and passes it on. */
  SummaryMemo.Core keep(final LocalMethod method, final SummaryMemo.Core core) {
    cores.put(method.key(), core);
    return core;
  }

  private boolean reaches(final LocalMethod method) {
    final Set<String> called = names.computeIfAbsent(method.key(), key -> namesOf(method));
    for (final String name : changed) {
      if (called.contains(name)) {
        return true;
      }
    }
    return false;
  }

  /** The called names, and the validators that the uses of validated arguments name. */
  private static Set<String> namesOf(final LocalMethod method) {
    final Set<String> called = new HashSet<>();
    for (final BasicBlock block : method.graph().blocks()) {
      for (final FlowSite site : block.sites()) {
        called.add(site.name());
        if (site.kind() == FlowSiteKind.VALIDATED) {
          called.add(validator(site.name()));
        }
      }
    }
    return called;
  }

  /** The validator that a use named {@code what:function@index} names; the whole name otherwise. */
  private static String validator(final String use) {
    final int start = use.indexOf(':');
    final int end = use.lastIndexOf('@');
    return start < 0 || end <= start ? use : use.substring(start + 1, end);
  }
}
