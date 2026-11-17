package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.WorkLimit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Summarizes the units of a run and resolves the summaries of each language into one graph. A unit
 * whose language has no summarizer contributes no summary and no edges. A summary that is stored is
 * reused, so only the units that changed are parsed again. Those are parsed and summarized on as
 * many threads as the work limit allows; the summary cache is read and written by the calling
 * thread only, in unit order.
 */
public final class DependencyAnalysis {
  private final Map<AnalysisLanguage, FrontendSupport> frontends;
  private final List<DependencySupport> dependencies;
  private final WorkLimit limit;

  /**
   * Creates the analysis that parses units with {@code frontends} and resolves with {@code
   * dependencies}, a unit at a time.
   */
  public DependencyAnalysis(
      final List<FrontendSupport> frontends,
      final List<DependencySupport> dependencies
  ) {
    this(frontends, dependencies, WorkLimit.serial());
  }

  /**
   * Like the constructor without {@code limit}, and summarizes as many units at once as {@code
   * limit} allows.
   */
  public DependencyAnalysis(
      final List<FrontendSupport> frontends,
      final List<DependencySupport> dependencies,
      final WorkLimit limit
  ) {
    this.frontends =
        frontends.stream()
            .collect(Collectors.toMap(FrontendSupport::language, frontend -> frontend));
    this.dependencies = List.copyOf(dependencies);
    this.limit = limit;
  }

  /**
   * Summarizes {@code units} and resolves them. Summaries are read from and written to {@code
   * cache}.
   *
   * @throws NullPointerException if the units or the cache are missing
   */
  public DependencySnapshot analyze(final List<SourceUnit> units, final FileSummaryCache cache) {
    return analyze(units, cache, EnumSet.allOf(AnalysisLanguage.class));
  }

  /**
   * Like {@link #analyze(List, FileSummaryCache)}, and parses only the units of {@code needed} and
   * of the languages that share a resolver with one of them, since an edge of a needed file can
   * reach a file of such a language.
   *
   * @throws NullPointerException if an argument is missing
   */
  public DependencySnapshot analyze(
      final List<SourceUnit> units,
      final FileSummaryCache cache,
      final Set<AnalysisLanguage> needed
  ) {
    final Set<AnalysisLanguage> parsed = withResolverPeers(needed);
    final Map<String, FileSummary> summaries = new TreeMap<>();
    final List<SourceUnit> fresh = new ArrayList<>();
    for (final SourceUnit unit : units) {
      if (parsed.contains(unit.language()) && summarized(unit)) {
        cache.read(unit)
            .ifPresentOrElse(summary -> summaries.put(unit.path(), summary), () -> fresh.add(unit));
      }
    }
    final List<Optional<FileSummary>> computed = limit.map(fresh, this::summarizeFresh);
    for (int i = 0; i < fresh.size(); i++) {
      final SourceUnit unit = fresh.get(i);
      computed
          .get(i)
          .ifPresent(
              summary -> {
                cache.write(unit, summary);
                summaries.put(unit.path(), summary);
              }
          );
    }
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final Map.Entry<DependencyResolver, Set<AnalysisLanguage>> group :
        ResolverGroups.of(dependencies).entrySet()) {
      edges.addAll(group.getKey().resolve(summariesOf(units, group.getValue(), summaries)));
    }
    return new DependencySnapshot(
        new DependencyGraph(edges),
        summaries,
        DependencyEntries.of(dependencies),
        units.stream().collect(Collectors.toMap(SourceUnit::path, unit -> unit, (a, b) -> a))
    );
  }

  private Set<AnalysisLanguage> withResolverPeers(final Set<AnalysisLanguage> needed) {
    final Set<AnalysisLanguage> parsed = EnumSet.noneOf(AnalysisLanguage.class);
    parsed.addAll(needed);
    for (final Set<AnalysisLanguage> group : ResolverGroups.of(dependencies).values()) {
      if (!Collections.disjoint(group, needed)) {
        parsed.addAll(group);
      }
    }
    return parsed;
  }

  private static List<FileSummary> summariesOf(
      final List<SourceUnit> units,
      final Set<AnalysisLanguage> languages,
      final Map<String,
      FileSummary> summaries
  ) {
    return units.stream()
        .filter(unit -> languages.contains(unit.language()))
        .map(unit -> summaries.get(unit.path()))
        .filter(Objects::nonNull)
        .toList();
  }

  /** Whether the language of {@code unit} has a summarizer and a frontend. */
  private boolean summarized(final SourceUnit unit) {
    return supportOf(unit.language()).isPresent() && frontends.containsKey(unit.language());
  }

  private Optional<FileSummary> summarizeFresh(final SourceUnit unit) {
    try {
      final ParseResult result = frontends.get(unit.language()).frontend().parse(unit);
      if (!result.diagnostics().isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          supportOf(unit.language()).orElseThrow().summarizer().summarize(unit, result.tree())
      );
    } catch (final AnalysisFailedException exception) {
      return Optional.empty();
    }
  }

  private Optional<DependencySupport> supportOf(final AnalysisLanguage language) {
    return dependencies.stream().filter(support -> support.language() == language).findFirst();
  }
}
