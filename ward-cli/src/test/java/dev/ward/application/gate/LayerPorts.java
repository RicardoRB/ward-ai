package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Runs {@code ward:architecture/layers} through the ward gate, for the layer gate tests. */
public final class LayerPorts {

  private LayerPorts() {}

  /** Returns the layer findings of {@code boundary} over {@code project}, resolving edges. */
  public static List<Finding> findings(
      final Project project,
      final LayerBoundary boundary,
      final ProjectDependencyAdapters dependencies) {
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(List.of(), List.of()),
            BundledRuleMatchers.create(),
            new FileSystemSourceUnits(),
            frontends(),
            projectRoot -> new FileAnalysisCache(projectRoot.resolve(".ward/cache/analysis")),
            AnalysisCacheFixtures.codec(),
            "test",
            dependencies);
    return new AnalysisGate("ward", List.of(LayerRules.definition(List.of(boundary))), ports, null)
        .execute(project).findings().items().stream()
            .filter(finding -> "ward:architecture/layers".equals(finding.ruleId()))
            .toList();
  }

  /**
   * The files that {@code ward:architecture/layers} flags in {@code project}, for {@code style}.
   */
  public static Set<String> layerFiles(
      final Project project, final String style, final ProjectDependencyAdapters dependencies) {
    return findings(project, ArchitectureLayers.forStyle(style, project.framework()), dependencies)
        .stream()
        .map(Finding::file)
        .collect(Collectors.toSet());
  }

  /** Every language of the layer rule has a frontend that provides symbols, as in the gate. */
  private static List<FrontendSupport> frontends() {
    final Set<RuleCapability> symbols = Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS);
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    return ArchitectureLayers.ENGINE_LANGUAGES.stream()
        .map(language -> new FrontendSupport(language, frontend, symbols))
        .toList();
  }
}
