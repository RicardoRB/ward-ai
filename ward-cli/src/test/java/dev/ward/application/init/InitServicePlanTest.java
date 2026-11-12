package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.ProjectSnapshot;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.infrastructure.agent.ClaudeCodeIntegration;
import dev.ward.infrastructure.init.ArchitectureTablesFixtures;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.QualityHookInstaller;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitServicePlanTest {

  @Test
  void standardClaudeAndHooksListEveryFileAndWriteNothing(@TempDir final Path dir)
      throws IOException {
    withPom(dir, PLAIN_POM);
    final Map<String, String> before = ProjectSnapshot.of(dir);

    final InitPlan plan =
        service(new PomToolConfigurator())
            .plan(dir, selection(List.of("claude-code"), List.of(), true), false);

    assertThat(actions(dir, plan))
        .containsEntry("ward.toml", Action.CREATE)
        .containsEntry(".claude/settings.json", Action.CREATE)
        .containsEntry(".claude/hooks/ward-edit-write", Action.CREATE)
        .containsEntry(".git/hooks/pre-commit", Action.CREATE)
        .containsEntry(".git/hooks/pre-push", Action.CREATE)
        .containsEntry("pom.xml", Action.MODIFY)
        .containsEntry("ward-rules.toml", Action.CREATE)
        .doesNotContainKeys(
            "config/pmd-ruleset.xml", "config/checkstyle.xml", "config/spotbugs-exclude.xml");
    assertThat(plan.hasConflict()).isFalse();
    assertThat(ProjectSnapshot.of(dir)).isEqualTo(before);
  }

  @Test
  void userOwnedIntegrationTargetIsAConflict(@TempDir final Path dir) throws IOException {
    withPom(dir, PLAIN_POM);
    Files.createDirectories(dir.resolve(".claude"));
    Files.writeString(dir.resolve(".claude/settings.json"), "{ not json");

    final InitPlan plan =
        service(new PomToolConfigurator())
            .plan(dir, selection(List.of("claude-code"), List.of(), false), false);

    assertThat(plan.hasConflict()).isTrue();
  }

  @Test
  void aMissingGateExecutableIsDescribedWithoutRunningAnInstaller(@TempDir final Path dir)
      throws IOException {
    withPom(dir, PLAIN_POM);
    final Map<String, String> before = ProjectSnapshot.of(dir);

    final InitPlan plan =
        service(new PomToolConfigurator())
            .plan(dir, new InitSelection(List.of("none"), List.of("tests", "gitleaks")), false);

    assertThat(plan.gateInstallations()).extracting("tool").containsExactly("gitleaks");
    assertThat(ProjectSnapshot.of(dir)).isEqualTo(before);
  }

  @Test
  void configurationWithoutPartialTablesIsPlannedAsModified(@TempDir final Path dir)
      throws IOException {
    withPom(dir, PLAIN_POM);
    final InitService service = service(new PomToolConfigurator());
    final InitSelection selection = selection(List.of("none"), List.of(), false);
    service.initialize(dir, selection);
    final Path configuration = dir.resolve("ward.toml");
    final String written = Files.readString(configuration);

    assertThat(written).contains("[checks.ward-format.paths]");
    assertThat(actions(dir, service.plan(dir, selection, true)))
        .containsEntry("ward.toml", Action.UNCHANGED);

    Files.writeString(
        configuration, written.replaceAll("(?s)\\n\\[checks\\.[a-z-]+\\.paths\\][^\\[]*", "\n"));

    assertThat(Files.readString(configuration)).doesNotContain(".paths]");
    assertThat(actions(dir, service.plan(dir, selection, true)))
        .containsEntry("ward.toml", Action.MODIFY);
  }

  private static InitSelection selection(
      final List<String> integrations, final List<String> install, final boolean hooks) {
    return new InitSelection(
        integrations,
        JavaPresetGates.STANDARD,
        null,
        QualityPreset.STANDARD,
        install,
        hooks,
        null,
        false,
        null);
  }

  private static InitService service(final PomToolConfigurator pom) {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(new ClaudeCodeIntegration()),
        new InitSetupCatalog(new FakeEnvironmentInspection(), new QualityHookInstaller()),
        new InitProjectFiles(
            pom,
            RuleFileTargetsFixtures.writer(),
            ArchitectureTablesFixtures.create(),
            new FileConfigurationStorage(),
            new OwnedSectionWriter()));
  }

  private static Map<String, Action> actions(final Path dir, final InitPlan plan) {
    return plan.files().stream()
        .collect(
            Collectors.toMap(
                change -> dir.relativize(change.file()).toString(), PlannedFileChange::action));
  }
}
