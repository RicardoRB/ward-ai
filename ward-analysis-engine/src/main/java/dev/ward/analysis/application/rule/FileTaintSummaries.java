package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.flow.taint.TaintFileSummary;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.check.CheckedRule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The taint summary of a file for the taint rules of a run: for each rule that reads the bodies of
 * other files, what the file's calls take from the files it depends on, see {@code
 * RuleCheck#flowSummary}. The files it depends on are those of {@link
 * DependencySnapshot#closureOf}, which include the files that implement a type they declare. Two
 * states of a run that give a file the same summary give it the same findings, so a cached result
 * can be keyed on the summary instead of on the text of the other files.
 */
public final class FileTaintSummaries {
  private final List<PreparedRule> taintRules;
  private final RelatedFlows flows;
  private final Map<AnalysisLanguage, Optional<List<TaintSpec>>> specsByLanguage =
      new ConcurrentHashMap<>();

  FileTaintSummaries(
      final Map<AnalysisLanguage,
      FrontendSupport> support,
      final List<PreparedRule> prepared,
      final DependencySnapshot snapshot
  ) {
    this.taintRules =
        prepared.stream()
            .filter(
                rule ->
                    rule.rule()
                        .filter(
                            compiled -> compiled.definition().capability().readsDependencyBodies()
                        )
                        .isPresent()
            )
            .toList();
    this.flows = new RelatedFlows(support, snapshot);
  }

  /**
   * The summary of the file of {@code unit}: empty text when no taint rule covers its language, and
   * no summary when a taint rule cannot give one, so the caller falls back on the text of the
   * files around the unit. Safe to call from several threads.
   */
  public Optional<String> of(final SourceUnit unit) {
    final Optional<List<TaintSpec>> specs =
        specsByLanguage.computeIfAbsent(unit.language(), this::specsOf);
    if (specs.isEmpty()) {
      return Optional.empty();
    }
    if (specs.get().isEmpty()) {
      return Optional.of("");
    }
    final List<ControlFlowGraph> own = flows.graphsOf(unit);
    return Optional.of(TaintFileSummary.of(own, flows.relatedOf(unit, own), specs.get()));
  }

  /** The distinct specifications of the taint rules of the language, or none when one has none. */
  private Optional<List<TaintSpec>> specsOf(final AnalysisLanguage language) {
    final List<Optional<TaintSpec>> specs =
        taintRules.stream()
            .filter(prepared -> prepared.runnable().contains(language))
            .flatMap(prepared -> prepared.rule().stream())
            .map(FileTaintSummaries::specOf)
            .toList();
    if (specs.stream().anyMatch(Optional::isEmpty)) {
      return Optional.empty();
    }
    return Optional.of(specs.stream().flatMap(Optional::stream).distinct().toList());
  }

  private static Optional<TaintSpec> specOf(final CompiledRule rule) {
    return rule instanceof CheckedRule checked ? checked.getCheck().taintSpec() : Optional.empty();
  }
}
