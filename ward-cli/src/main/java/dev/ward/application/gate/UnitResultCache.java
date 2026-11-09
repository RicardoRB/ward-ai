package dev.ward.application.gate;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.DuplicateCodeFindings;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisFindingOrder;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.DuplicateCodeRule;
import dev.ward.analysis.domain.rule.GraphRules;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs the rules over the units whose results are not stored, and reuses the stored results of the
 * others. Only a run with no execution error and no rule that did not run is stored, so a stored
 * result always came from a complete analysis. Such a run also rewrites the reverse-dependency
 * index, so a missing or corrupt index is rebuilt.
 */
final class UnitResultCache {
  private final RuleEngine engine;
  private final AnalysisResultCodec codec;
  private final String engineVersion;
  private final List<DependencySupport> dependencies;

  /** Creates the partition that analyzes missing units with {@code engine}. */
  UnitResultCache(
      final RuleEngine engine,
      final AnalysisResultCodec codec,
      final String engineVersion,
      final List<DependencySupport> dependencies) {
    this.engine = engine;
    this.codec = codec;
    this.engineVersion = engineVersion;
    this.dependencies = List.copyOf(dependencies);
  }

  /** The findings of a run, in the stable report order, and the dependency graph it resolved. */
  record CachedRun(RuleRunResult result, DependencyGraph graph) {}

  /** Returns the findings of every unit, reused or analyzed, in the stable report order. */
  RuleRunResult run(
      final AnalysisCache cache,
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules) {
    return analyze(cache, units, frontends, rules).result();
  }

  /** Like {@link #run}, and also returns the dependency graph of the whole run. */
  CachedRun analyze(
      final AnalysisCache cache,
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules) {
    final List<RuleDefinition> unitRules =
        rules.stream()
            .filter(rule -> !GraphRules.isGraphRule(rule))
            .filter(rule -> !DuplicateCodeRule.isDuplicateRule(rule))
            .filter(rule -> !UnusedDefinitionFindings.isUnusedDefinitionRule(rule))
            .toList();
    final DependencySnapshot snapshot =
        new DependencyAnalysis(frontends, dependencies)
            .analyze(units, new AnalysisSummaryCache(cache, codec, engineVersion));
    final Map<SourceUnit, String> keys = new LinkedHashMap<>();
    final List<AnalysisFinding> reused = new ArrayList<>();
    final List<SourceUnit> misses = new ArrayList<>();
    final Map<AnalysisLanguage, String> ruleSets = new EnumMap<>(AnalysisLanguage.class);
    for (final SourceUnit unit : units) {
      final String ruleSet =
          ruleSets.computeIfAbsent(unit.language(), language -> ruleSet(language, unitRules));
      final String key = key(ruleSet, unit, unitRules, snapshot);
      keys.put(unit, key);
      final Optional<List<AnalysisFinding>> stored = read(cache, key);
      if (stored.isPresent()) {
        reused.addAll(stored.get());
      } else {
        misses.add(unit);
      }
    }
    final RuleRunResult analyzed = engine.run(misses, frontends, unitRules, snapshot);
    if (analyzed.executionErrors().isEmpty() && analyzed.notRun().isEmpty()) {
      store(cache, keys, misses, analyzed.findings());
      cache.write(AnalysisCacheKey.index(), codec.encodeIndex(snapshot.graph()));
    }
    final List<AnalysisFinding> wholeRunFindings =
        new ArrayList<>(
            ArchitectureGraphRules.findings(
                rules,
                snapshot.graph(),
                new ProjectFiles(snapshot.summaries().keySet(), snapshot.entries())));
    wholeRunFindings.addAll(DuplicateCodeFindings.findings(rules, units, frontends));
    wholeRunFindings.addAll(
        UnusedDefinitionFindings.findings(rules, snapshot.summaries(), moduleOf()));
    wholeRunFindings.addAll(engine.runProject(units, frontends, rules));
    return new CachedRun(merged(reused, analyzed, wholeRunFindings), snapshot.graph());
  }

  /**
   * The module of a file, as the first dependency adapter that knows the file's module names it.
   */
  private Function<String, Optional<String>> moduleOf() {
    return path ->
        dependencies.stream()
            .map(support -> support.resolver().moduleOf(path))
            .flatMap(Optional::stream)
            .findFirst();
  }

  private String ruleSet(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return AnalysisCacheKey.ruleSet(
        engineVersion, rules.stream().filter(rule -> rule.covers(language)).toList());
  }

  /** A language whose rules read dependencies keys each unit on the files around it too. */
  private String key(
      final String ruleSet,
      final SourceUnit unit,
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot) {
    if (!readsDependencies(unit.language(), rules)) {
      return AnalysisCacheKey.unit(ruleSet, unit);
    }
    return AnalysisCacheKey.unit(ruleSet, unit, snapshot.view(unit.path()));
  }

  private static boolean readsDependencies(
      final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(rule -> rule.covers(language) && rule.capability().readsDependencies());
  }

  private Optional<List<AnalysisFinding>> read(final AnalysisCache cache, final String key) {
    return cache.read(key).flatMap(value -> codec.decode(key, value));
  }

  private void store(
      final AnalysisCache cache,
      final Map<SourceUnit, String> keys,
      final List<SourceUnit> misses,
      final List<AnalysisFinding> analyzed) {
    final Map<String, List<AnalysisFinding>> byFile =
        analyzed.stream().collect(Collectors.groupingBy(AnalysisFinding::file));
    for (final SourceUnit unit : misses) {
      final List<AnalysisFinding> findings = byFile.getOrDefault(unit.path(), List.of());
      final String key = keys.get(unit);
      cache.write(key, codec.encode(key, findings));
    }
  }

  private static RuleRunResult merged(
      final List<AnalysisFinding> reused,
      final RuleRunResult analyzed,
      final List<AnalysisFinding> graphFindings) {
    final List<AnalysisFinding> all = new ArrayList<>(reused);
    all.addAll(analyzed.findings());
    all.addAll(graphFindings);
    all.sort(AnalysisFindingOrder.ORDER);
    return new RuleRunResult(all, analyzed.executionErrors(), analyzed.notRun());
  }
}
