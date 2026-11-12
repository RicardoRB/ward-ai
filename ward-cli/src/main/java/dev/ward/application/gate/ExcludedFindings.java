package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.List;

/** Drops the findings of a rule in the paths that rule leaves out in {@code ward-rules.toml}. */
final class ExcludedFindings {
  private ExcludedFindings() {}

  /**
   * Returns {@code cached} without the excluded findings. The filter runs after the cache, so a
   * changed {@code exclude-paths} needs no re-analysis.
   */
  static UnitResultCache.CachedRun drop(final UnitResultCache.CachedRun cached, final RuleSet set) {
    if (set.excludePaths().isEmpty()) {
      return cached;
    }
    final RuleRunResult run = cached.result();
    final List<AnalysisFinding> kept =
        run.findings().stream().filter(finding -> !isExcluded(finding, set)).toList();
    return new UnitResultCache.CachedRun(
        new RuleRunResult(kept, run.executionErrors(), run.notRun()), cached.graph());
  }

  /** Returns {@code run} without the findings that the rule set leaves out. */
  static RuleRunResult drop(final RuleRunResult run, final RuleSet set) {
    return drop(new UnitResultCache.CachedRun(run, null), set).result();
  }

  private static boolean isExcluded(final AnalysisFinding finding, final RuleSet set) {
    return finding.file() != null
        && set.excludePaths().getOrDefault(finding.ruleId(), List.of()).stream()
            .anyMatch(pattern -> pattern.matcher(finding.file()).matches());
  }
}
