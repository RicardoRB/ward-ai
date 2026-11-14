package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs a rule over units. A rule that follows calls into other files gets the dependencies that the
 * project's other sources give the units, as the {@code ward} gate resolves them.
 */
final class DependentRun {

  private DependentRun() {}

  static RuleRunResult run(
      final RuleEngine engine,
      final RuleDefinition rule,
      final List<SourceUnit> units,
      final List<SourceUnit> others,
      final List<FrontendSupport> support) {
    if (!rule.capability().readsDependencyBodies() || others.isEmpty()) {
      return engine.run(units, support, List.of(rule));
    }
    final List<SourceUnit> all = new ArrayList<>(units);
    all.addAll(others);
    final DependencySnapshot snapshot =
        new DependencyAnalysis(support, new JvmDependencyAdapters().forProject(Path.of("."), all))
            .analyze(all, new NoSummaries());
    return engine.run(units, support, List.of(rule), snapshot);
  }

  /** Keeps no summaries, so every run resolves the project again. */
  private static final class NoSummaries implements FileSummaryCache {
    @Override
    public Optional<FileSummary> read(final SourceUnit unit) {
      return Optional.empty();
    }

    @Override
    public void write(final SourceUnit unit, final FileSummary summary) {
      // nothing is kept
    }
  }
}
