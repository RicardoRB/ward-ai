package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** One round of the method summaries of a file, see {@link TaintFile}. */
final class SummaryRounds {
  private SummaryRounds() {}

  /** The fingerprint of each method, read once for the rounds of a file. */
  static Map<String, SummaryMemo.Print> prints(final Map<String, LocalMethod> methods) {
    final Map<String, SummaryMemo.Print> prints = new HashMap<>();
    methods.forEach((key, method) -> prints.put(key, print(method)));
    return prints;
  }

  private static SummaryMemo.Print print(final LocalMethod method) {
    return new SummaryMemo.Print(method);
  }

  private static SummaryMemo.Key key(
      final SummaryMemo.Print print,
      final SummaryMemo.Matched matched
  ) {
    return new SummaryMemo.Key(print, matched);
  }

  /**
   * The summary of each method that has one, given the specification of the round and what the
   * last round found. The analysis of a method comes from the memo when another file of the run
   * reached it with the same matching entries.
   */
  static Map<String, MethodSummary> summarize(
      final TaintSpec spec,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      MethodSummary> previous,
      final SummaryMemo memo,
      final Map<String,
      SummaryMemo.Print> prints,
      final RoundCores cores
  ) {
    final SpecIndex index = new SpecIndex(spec);
    final Set<String> kinds = ValidatingMethods.kinds(spec);
    final Map<String, MethodSummary> found = new HashMap<>();
    final TaintSpec seeded = withParameterSource(spec);
    final Map<String, Map<String, Set<Integer>>> validating =
        previous.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().validated())
        );
    for (final LocalMethod method : methods.values()) {
      final SummaryMemo.Core core =
          cores.reuse(method)
              .orElseGet(
                  () -> cores.keep(
                      method,
                      memo.core(
                          key(prints.get(method.key()), index.matched(method.graph())),
                          () -> MethodSummary.core(method, spec, seeded)
                      )
                  )
              );
      MethodSummary.of(method, core, kinds, methods, validating)
          .ifPresent(summary -> found.put(method.key(), summary));
    }
    return found;
  }

  /**
   * The names that the last round changed: the methods whose summary is not the one before. A
   * method that names none of them in a call, or in a use of a validated argument, gets the same
   * matching entries again.
   */
  static Set<String> changed(
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      MethodSummary> before,
      final Map<String,
      MethodSummary> after
  ) {
    final Set<String> names = new HashSet<>();
    for (final String key : methods.keySet()) {
      if (!Objects.equals(before.get(key), after.get(key))) {
        names.add(methods.get(key).name());
      }
    }
    return names;
  }

  static TaintSpec withParameterSource(final TaintSpec spec) {
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    sources.add(
        new TaintEntry(
            FlowSiteKind.PARAMETER,
            LocalMethod.mark(),
            Optional.empty(),
            Optional.empty(),
            List.of()
        )
    );
    return new TaintSpec(sources, spec.sinks(), spec.sanitizers(), spec.propagators());
  }
}
