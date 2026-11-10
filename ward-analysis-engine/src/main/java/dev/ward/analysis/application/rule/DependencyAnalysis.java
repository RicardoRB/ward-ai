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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Summarizes the units of a run and resolves the summaries of each language into one graph. A unit
 * whose language has no summarizer contributes no summary and no edges. A summary that is stored is
 * reused, so only the units that changed are parsed again.
 */
public final class DependencyAnalysis {
  private final Map<AnalysisLanguage, FrontendSupport> frontends;
  private final List<DependencySupport> dependencies;

  /**
   * Creates the analysis that parses units with {@code frontends} and resolves with {@code
   * dependencies}.
   */
  public DependencyAnalysis(
      final List<FrontendSupport> frontends, final List<DependencySupport> dependencies) {
    this.frontends = byLanguage(frontends, FrontendSupport::language);
    this.dependencies = List.copyOf(dependencies);
  }

  /**
   * Summarizes {@code units} and resolves them. Summaries are read from and written to {@code
   * cache}.
   *
   * @throws NullPointerException if the units or the cache are missing
   */
  public DependencySnapshot analyze(final List<SourceUnit> units, final FileSummaryCache cache) {
    final Map<String, FileSummary> summaries = new TreeMap<>();
    for (final SourceUnit unit : units) {
      summarize(unit, cache).ifPresent(summary -> summaries.put(unit.path(), summary));
    }
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final Map.Entry<DependencyResolver, Set<AnalysisLanguage>> group :
        ResolverGroups.of(dependencies).entrySet()) {
      edges.addAll(group.getKey().resolve(summariesOf(units, group.getValue(), summaries)));
    }
    return new DependencySnapshot(
        new DependencyGraph(edges), summaries, DependencyEntries.of(dependencies));
  }

  private static List<FileSummary> summariesOf(
      final List<SourceUnit> units,
      final Set<AnalysisLanguage> languages,
      final Map<String, FileSummary> summaries) {
    return units.stream()
        .filter(unit -> languages.contains(unit.language()))
        .map(unit -> summaries.get(unit.path()))
        .filter(Objects::nonNull)
        .toList();
  }

  private Optional<FileSummary> summarize(final SourceUnit unit, final FileSummaryCache cache) {
    final Optional<DependencySupport> support = supportOf(unit.language());
    if (support.isEmpty() || !frontends.containsKey(unit.language())) {
      return Optional.empty();
    }
    final Optional<FileSummary> stored = cache.read(unit);
    if (stored.isPresent()) {
      return stored;
    }
    final Optional<FileSummary> fresh = summarizeFresh(unit, support.get());
    fresh.ifPresent(summary -> cache.write(unit, summary));
    return fresh;
  }

  private Optional<FileSummary> summarizeFresh(
      final SourceUnit unit, final DependencySupport support) {
    try {
      final ParseResult result = frontends.get(unit.language()).frontend().parse(unit);
      if (!result.diagnostics().isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(support.summarizer().summarize(unit, result.tree()));
    } catch (final AnalysisFailedException exception) {
      return Optional.empty();
    }
  }

  private Optional<DependencySupport> supportOf(final AnalysisLanguage language) {
    return dependencies.stream().filter(support -> support.language() == language).findFirst();
  }

  private static <T> Map<AnalysisLanguage, T> byLanguage(
      final List<T> items, final Function<T, AnalysisLanguage> language) {
    return items.stream().collect(Collectors.toMap(language, Function.identity()));
  }
}
