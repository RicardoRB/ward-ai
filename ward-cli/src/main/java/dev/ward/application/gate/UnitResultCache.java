package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisFindingOrder;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs the rules over the units whose results are not stored, and reuses the stored results of the
 * others. Only a run with no execution error and no rule that did not run is stored, so a stored
 * result always came from a complete analysis.
 */
final class UnitResultCache {
  private final RuleEngine engine;
  private final AnalysisResultCodec codec;
  private final String engineVersion;

  /** Creates the partition that analyzes missing units with {@code engine}. */
  UnitResultCache(
      final RuleEngine engine, final AnalysisResultCodec codec, final String engineVersion) {
    this.engine = engine;
    this.codec = codec;
    this.engineVersion = engineVersion;
  }

  /** Returns the findings of every unit, reused or analyzed, in the stable report order. */
  RuleRunResult run(
      final AnalysisCache cache,
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules) {
    final Map<SourceUnit, String> keys = new LinkedHashMap<>();
    final List<AnalysisFinding> reused = new ArrayList<>();
    final List<SourceUnit> misses = new ArrayList<>();
    final Map<AnalysisLanguage, String> ruleSets = new EnumMap<>(AnalysisLanguage.class);
    for (final SourceUnit unit : units) {
      final String ruleSet =
          ruleSets.computeIfAbsent(unit.language(), language -> ruleSet(language, rules));
      final String key = AnalysisCacheKey.unit(ruleSet, unit);
      keys.put(unit, key);
      final Optional<List<AnalysisFinding>> stored = read(cache, key);
      if (stored.isPresent()) {
        reused.addAll(stored.get());
      } else {
        misses.add(unit);
      }
    }
    final RuleRunResult analyzed = engine.run(misses, frontends, rules);
    if (analyzed.executionErrors().isEmpty() && analyzed.notRun().isEmpty()) {
      store(cache, keys, misses, analyzed.findings());
    }
    return merged(reused, analyzed);
  }

  private String ruleSet(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return AnalysisCacheKey.ruleSet(
        engineVersion, rules.stream().filter(rule -> rule.language() == language).toList());
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
      final List<AnalysisFinding> reused, final RuleRunResult analyzed) {
    final List<AnalysisFinding> all = new ArrayList<>(reused);
    all.addAll(analyzed.findings());
    all.sort(AnalysisFindingOrder.ORDER);
    return new RuleRunResult(all, analyzed.executionErrors(), analyzed.notRun());
  }
}
