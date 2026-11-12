package dev.ward.application.gate;

import dev.ward.analysis.application.rule.TextRules;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisFindingOrder;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs the {@code TEXT} rules over the files whose results are not stored, and reuses the stored
 * results of the others. A result is stored per file under the hash of the engine version, the
 * rules with their options and the file's path and text, so a changed file or option runs again.
 * Only a run with no execution error is stored.
 */
final class TextResultCache {
  private final RuleMatcher matcher;
  private final AnalysisResultCodec codec;
  private final String engineVersion;

  TextResultCache(
      final RuleMatcher matcher, final AnalysisResultCodec codec, final String engineVersion) {
    this.matcher = matcher;
    this.codec = codec;
    this.engineVersion = engineVersion;
  }

  /** Returns the findings of every file, reused or analyzed, in the stable report order. */
  RuleRunResult run(
      final AnalysisCache cache, final List<TextFile> files, final List<RuleDefinition> rules) {
    final String ruleSet = AnalysisCacheKey.ruleSet(engineVersion, rules);
    final Map<TextFile, String> misses = new LinkedHashMap<>();
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final TextFile file : files) {
      final String key = AnalysisCacheKey.text(ruleSet, file);
      final Optional<List<AnalysisFinding>> stored =
          cache.read(key).flatMap(value -> codec.decode(key, value));
      if (stored.isPresent()) {
        findings.addAll(stored.get());
      } else {
        misses.put(file, key);
      }
    }
    final RuleRunResult analyzed = new TextRules(matcher, rules).run(List.copyOf(misses.keySet()));
    if (analyzed.executionErrors().isEmpty()) {
      store(cache, misses, analyzed.findings());
    }
    findings.addAll(analyzed.findings());
    findings.sort(AnalysisFindingOrder.ORDER);
    return new RuleRunResult(findings, analyzed.executionErrors(), analyzed.notRun());
  }

  private void store(
      final AnalysisCache cache,
      final Map<TextFile, String> misses,
      final List<AnalysisFinding> analyzed) {
    final Map<String, List<AnalysisFinding>> byFile =
        analyzed.stream().collect(Collectors.groupingBy(AnalysisFinding::file));
    misses.forEach(
        (file, key) ->
            cache.write(key, codec.encode(key, byFile.getOrDefault(file.path(), List.of()))));
  }
}
