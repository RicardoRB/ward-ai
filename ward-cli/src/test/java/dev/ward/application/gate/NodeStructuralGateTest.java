package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.analysis.infrastructure.node.NodeRuleMatcher;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code ward} gate with the bundled TypeScript and JavaScript rules, on a real project. */
class NodeStructuralGateTest {

  private static final String NAMING = "ward:typescript/naming";
  private static final String UNKNOWN = "ward:typescript/no-such-rule";

  @TempDir Path root;

  @Test
  void runsTheNamedNodeRuleAndReportsItAsAWardFinding() throws IOException {
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.ts"), "export const user_name = 1;\n");

    final GateResult result = gate(List.of(NAMING)).execute(project());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.tool()).isEqualTo("ward");
              assertThat(finding.ruleId()).isEqualTo(NAMING);
              assertThat(finding.file()).isEqualTo("src/app.ts");
              assertThat(finding.line()).isEqualTo(1);
            });
  }

  @Test
  void twoRunsOverTheSameProjectReportTheSameFindings() throws IOException {
    Files.writeString(root.resolve("app.ts"), "const a_b = 1;\nconst c_d = 2;\n");
    final Project project = project();

    final List<Finding> first = findings(gate(List.of(NAMING)).execute(project));
    final List<Finding> second = findings(gate(List.of(NAMING)).execute(project));

    assertThat(first).hasSize(2).isEqualTo(second);
  }

  @Test
  void aNameThatIsNotABundledRuleFailsTheConfiguration() {
    assertThatThrownBy(() -> gate(List.of(UNKNOWN)))
        .hasMessageContaining("is not a known analysis rule");
  }

  private static List<Finding> findings(final GateResult result) {
    return result.findings().items();
  }

  private static QualityGate gate(final List<String> rules) {
    final GateConfiguration check =
        new GateConfiguration(
            "structure",
            "ward",
            true,
            List.of(),
            Set.of(),
            null,
            null,
            null,
            null,
            rules,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return AnalysisGates.create(check, ports());
  }

  private static AnalysisPorts ports() {
    final Set<RuleCapability> syntax = Set.of(RuleCapability.SYNTAX);
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    return new AnalysisPorts(
        new ClasspathRuleSource(),
        new NodeRuleMatcher(),
        new FileSystemSourceUnits(new ProcessCommandExecutor()),
        List.of(
            new FrontendSupport(AnalysisLanguage.TYPESCRIPT, frontend, syntax),
            new FrontendSupport(AnalysisLanguage.JAVASCRIPT, frontend, syntax)),
        root -> new FileAnalysisCache(root.resolve(".ward/cache/analysis")),
        AnalysisCacheFixtures.codec(),
        "test",
        DependencyAdapters.none(),
        (unused, classpath) -> Map.of());
  }

  private Project project() {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        null,
        TaskRunner.NONE,
        null);
  }
}
