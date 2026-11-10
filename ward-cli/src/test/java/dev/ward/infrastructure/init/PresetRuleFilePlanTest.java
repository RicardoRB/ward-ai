package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetRuleFilePlanTest {
  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults());

  @Test
  void missingFilesAreCreatedWithoutTouchingTheProject(@TempDir final Path root) {
    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(writer.plan(project(root), QualityPreset.STANDARD, gates("enforcer")));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve("config/enforcer-rules.xml"), Action.CREATE));
    assertThat(root.resolve("config")).doesNotExist();
  }

  @Test
  void currentFileIsUnchangedAndOtherPresetIsModified(@TempDir final Path root) {
    writer.apply(project(root), QualityPreset.STANDARD, gates("enforcer"));

    assertThat(
            RuleFilePlans.tools(
                writer.plan(project(root), QualityPreset.STANDARD, gates("enforcer"))))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.UNCHANGED);
    assertThat(
            RuleFilePlans.tools(
                writer.plan(project(root), QualityPreset.STRICT, gates("enforcer"))))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.MODIFY);
  }

  @Test
  void unmarkedFileIsPreserved(@TempDir final Path root) throws IOException {
    Files.createDirectories(root.resolve("config"));
    Files.writeString(root.resolve("config/enforcer-rules.xml"), "<module name=\"Checker\"/>\n");

    assertThat(
            RuleFilePlans.tools(
                writer.plan(project(root), QualityPreset.STANDARD, gates("enforcer"))))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.PRESERVE_USER_OWNED);
  }

  @Test
  void noPresetPlansNothing(@TempDir final Path root) {
    assertThat(RuleFilePlans.tools(writer.plan(project(root), null, gates("enforcer")))).isEmpty();
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("mvn", id), true, "available"))
        .toList();
  }
}
