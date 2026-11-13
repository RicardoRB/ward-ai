package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectFixtures;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
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

/**
 * Init no longer writes {@code config/enforcer-rules.xml}; it retires the file an earlier init
 * wrote.
 */
class RetiredEnforcerRulesTest {
  private static final String RULES = "config/enforcer-rules.xml";
  private static final String OWNED =
      "<!-- managed-by: ward preset=strict -->\n<enforcer><rules/></enforcer>\n";

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults());

  @Test
  void aProjectWithoutTheFileGetsNoEnforcerFile(@TempDir final Path root) {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);

    assertThat(RuleFilePlans.tools(writer.plan(project(root), QualityPreset.STRICT, List.of())))
        .isEmpty();
    writer.apply(project(root), QualityPreset.STRICT, List.of());
    assertThat(root.resolve("config")).doesNotExist();
  }

  @Test
  void aWardOwnedFileIsRemovedAndRestoredOnRollback(@TempDir final Path root) throws IOException {
    write(root, OWNED);

    assertThat(RuleFilePlans.tools(writer.plan(project(root), QualityPreset.STRICT, List.of())))
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.file()).isEqualTo(root.resolve(RULES));
              assertThat(change.action()).isEqualTo(Action.REMOVE);
            });
    final RuleFileChange change = writer.apply(project(root), QualityPreset.STRICT, List.of());
    assertThat(root.resolve(RULES)).doesNotExist();

    writer.rollback(change);
    assertThat(Files.readString(root.resolve(RULES))).isEqualTo(OWNED);
  }

  @Test
  void aUserOwnedFileIsKeptAndReported(@TempDir final Path root) throws IOException {
    write(root, "<enforcer><rules><banDistributionManagement/></rules></enforcer>\n");

    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(writer.plan(project(root), QualityPreset.STRICT, List.of()));
    assertThat(plan)
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.PRESERVE_USER_OWNED);
    final RuleFileChange change = writer.apply(project(root), QualityPreset.STRICT, List.of());

    assertThat(root.resolve(RULES)).exists();
    assertThat(change.preserved()).containsExactly(root.resolve(RULES));
    assertThat(change.warnings())
        .singleElement()
        .asString()
        .contains("kept", "no longer used", "require-upper-bound");
  }

  private static void write(final Path root, final String content) throws IOException {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    Files.createDirectories(root.resolve("config"));
    Files.writeString(root.resolve(RULES), content);
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }
}
