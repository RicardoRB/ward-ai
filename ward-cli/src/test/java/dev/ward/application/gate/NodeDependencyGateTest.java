package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.analysis.infrastructure.node.NodeRuleMatcher;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code ward} gate over a Node workspace stores the reverse index with the cross-package edge.
 */
class NodeDependencyGateTest {
  @TempDir Path cacheRoot;

  @Test
  void storesTheCrossPackageEdgeInTheIndexOfTheTurborepoWorkspace()
      throws URISyntaxException, IOException {
    final Path root = Path.of(getClass().getResource("/fixtures/turborepo-workspace").toURI());
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(List.of(), List.of()),
            new NodeRuleMatcher(),
            new FileSystemSourceUnits(new ProcessCommandExecutor()),
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.TYPESCRIPT,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX)),
                new FrontendSupport(
                    AnalysisLanguage.JAVASCRIPT,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX))),
            projectRoot -> new FileAnalysisCache(cacheRoot),
            AnalysisCacheFixtures.codec(),
            "0.6.0",
            new NodeDependencyAdapters(),
            (unused, classpath) -> Map.of());
    final Project project = new Project(root, Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE);

    final GateResult result = new AnalysisGate("ward", List.of(), ports).execute(project);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    final String index =
        new FileAnalysisCache(cacheRoot).read(AnalysisCacheKey.index()).orElseThrow();
    assertThat(index).contains("apps/web/src/index.ts").contains("packages/ui/src/index.ts");
  }
}
