package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
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
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code ward} gate with the bundled Kotlin rules, on a real Gradle Kotlin project. */
class KotlinStructuralGateTest {

  private static final String TYPE_NAME = "ward:kotlin/type-name";
  private static final String UNKNOWN = "ward:kotlin/no-such-rule";
  private static final int CLASS_LINE = 3;

  @TempDir Path root;

  @Test
  void runsTheNamedKotlinRuleAndReportsItAsAWardFinding() throws IOException {
    Files.createDirectories(root.resolve("src/main/kotlin"));
    Files.writeString(root.resolve("src/main/kotlin/App.kt"), "package app\n\nclass userService\n");

    final GateResult result = gate(List.of(TYPE_NAME)).execute(project());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.tool()).isEqualTo("ward");
              assertThat(finding.ruleId()).isEqualTo(TYPE_NAME);
              assertThat(finding.file()).isEqualTo("src/main/kotlin/App.kt");
              assertThat(finding.line()).isEqualTo(CLASS_LINE);
            });
  }

  @Test
  void aCompliantKotlinProjectPassesTheGate() throws IOException {
    Files.writeString(root.resolve("App.kt"), "package app\n\nclass UserService\n");

    assertThat(gate(List.of(TYPE_NAME)).execute(project()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void twoRunsOverTheSameProjectReportTheSameFindings() throws IOException {
    Files.writeString(root.resolve("App.kt"), "package app\n\nclass a_b\nclass c_d\n");
    final Project project = project();

    final List<Finding> first = findings(gate(List.of(TYPE_NAME)).execute(project));
    final List<Finding> second = findings(gate(List.of(TYPE_NAME)).execute(project));

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
    return new AnalysisPorts(
        new ClasspathRuleSource(),
        new DeclarativeRuleMatcher(),
        new FileSystemSourceUnits(new ProcessCommandExecutor()),
        List.of(
            new FrontendSupport(
                AnalysisLanguage.KOTLIN,
                new AntlrLanguageFrontend(),
                Set.of(RuleCapability.SYNTAX))),
        root -> AnalysisCacheFixtures.NO_CACHE,
        AnalysisCacheFixtures.codec(),
        "test",
        DependencyAdapters.none(),
        (unused, classpath) -> Map.of());
  }

  private Project project() {
    return new Project(
        root,
        Language.KOTLIN,
        BuildTool.GRADLE,
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
