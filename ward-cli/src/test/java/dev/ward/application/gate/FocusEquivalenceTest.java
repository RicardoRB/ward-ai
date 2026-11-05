package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
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

/** Every finding of a narrowed {@code ward} run is the same finding a full run reports. */
class FocusEquivalenceTest {
  private static final String JAVA_ORDER = "src/main/java/acme/order.java";
  private static final String JAVA_CUSTOMER = "src/main/java/acme/customer.java";
  private static final String KOTLIN_APP = "src/main/kotlin/App.kt";

  @TempDir Path root;

  @Test
  void narrowedJavaFindingsAreTheFullRunsFindingsForThatFile() throws IOException {
    writeTree();

    final List<Finding> full = findings(List.of());
    final List<Finding> narrowed = findings(List.of(JAVA_ORDER));

    assertThat(narrowed).isNotEmpty().allMatch(full::contains);
    assertThat(narrowed).isEqualTo(filesOf(full, JAVA_ORDER));
  }

  @Test
  void narrowedKotlinFindingsAreTheFullRunsFindingsForThatFile() throws IOException {
    writeTree();

    final List<Finding> full = findings(List.of());
    final List<Finding> narrowed = findings(List.of(KOTLIN_APP));

    assertThat(narrowed).isNotEmpty().allMatch(full::contains);
    assertThat(narrowed).isEqualTo(filesOf(full, KOTLIN_APP));
  }

  private void writeTree() throws IOException {
    Files.createDirectories(root.resolve("src/main/java/acme"));
    Files.createDirectories(root.resolve("src/main/kotlin"));
    Files.writeString(
        root.resolve(JAVA_ORDER), "package acme;\n\nclass order {\n  void Run() {}\n}\n");
    Files.writeString(root.resolve(JAVA_CUSTOMER), "package acme;\n\nclass customer {}\n");
    Files.writeString(root.resolve(KOTLIN_APP), "package app\n\nclass userService\n");
  }

  private List<Finding> findings(final List<String> focus) {
    final QualityGate gate = gate(focus);
    final GateResult result = gate.execute(project());
    return result.findings().items();
  }

  private static List<Finding> filesOf(final List<Finding> findings, final String file) {
    return findings.stream().filter(finding -> file.equals(finding.file())).toList();
  }

  private static QualityGate gate(final List<String> focus) {
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
            null,
            List.of("ward:java/type-name", "ward:java/member-name", "ward:kotlin/type-name"),
            null,
            List.of());
    return AnalysisGates.create(check, ports(), focus);
  }

  private static AnalysisPorts ports() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    return new AnalysisPorts(
        new ClasspathRuleSource(),
        new DeclarativeRuleMatcher(),
        new FileSystemSourceUnits(),
        List.of(
            new FrontendSupport(AnalysisLanguage.JAVA, frontend, Set.of(RuleCapability.SYNTAX)),
            new FrontendSupport(AnalysisLanguage.KOTLIN, frontend, Set.of(RuleCapability.SYNTAX))),
        root -> AnalysisCacheFixtures.NO_CACHE,
        AnalysisCacheFixtures.codec(),
        "test",
        DependencyAdapters.none(),
        unused -> Map.of());
  }

  private Project project() {
    return new Project(
        root,
        Language.JAVA,
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
