package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaceMembers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link CheckService} with the real {@code ward} analysis, for partial checks of Java sources.
 */
final class WardAnalysisFixtures {
  private WardAnalysisFixtures() {}

  /** A check service whose gate commands run through {@code executor}. */
  static CheckService checks(final FakeCommandExecutor executor) {
    final QualityGateFactory factory =
        new QualityGateFactory(
            executor,
            null,
            new GateReportPorts(ReportDirectories.NONE, null, null, null),
            new GateExtensions(null, ports(), null));
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        factory,
        new QualityGateRunner(),
        new PartialPorts(new FilePathExistence(), new NodeWorkspaceMembers()));
  }

  private static AnalysisPorts ports() {
    return new AnalysisPorts(
        new ClasspathRuleSource(),
        new DeclarativeRuleMatcher(),
        new FileSystemSourceUnits(new ProcessCommandExecutor()),
        List.of(
            new FrontendSupport(
                AnalysisLanguage.JAVA, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX))),
        root -> AnalysisCacheFixtures.NO_CACHE,
        AnalysisCacheFixtures.codec(),
        "test",
        DependencyAdapters.none(),
        (unused, classpath) -> Map.of());
  }
}
