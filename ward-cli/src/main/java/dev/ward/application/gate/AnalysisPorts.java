package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What the analysis gate needs from its adapters.
 *
 * @param rules loads the rule files
 * @param matcher builds rules from their bodies
 * @param sources reads the source files
 * @param frontends the language frontends and the capabilities they provide
 * @param caches opens the result cache of a project
 * @param codec encodes the cached results
 * @param engineVersion the Ward version, which invalidates cached results when it changes
 * @param dependencies builds the dependency adapters of each run; {@link DependencyAdapters#none()}
 *     when no language resolves dependencies
 * @param typeSidecars opens the optional type sidecars of a project, by language; a language
 *     without one has no types
 */
public record AnalysisPorts(
    AnalysisRuleSource rules,
    RuleMatcher matcher,
    AnalysisSourceUnits sources,
    List<FrontendSupport> frontends,
    AnalysisCaches caches,
    AnalysisResultCodec codec,
    String engineVersion,
    DependencyAdapters dependencies,
    Function<Path, Map<AnalysisLanguage, TypeSidecar>> typeSidecars) {

  /** Copies the frontends. */
  public AnalysisPorts {
    frontends = List.copyOf(frontends);
  }
}
