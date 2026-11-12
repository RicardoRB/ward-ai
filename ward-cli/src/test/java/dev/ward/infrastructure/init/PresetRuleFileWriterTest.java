package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectFixtures;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetRuleFileWriterTest {
  @Test
  void writesOwnedFilesAndRestoresCreatedFiles(@TempDir final Path root) throws Exception {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final List<InitGateOption> gates = List.of(gate("enforcer"));
    final PresetRuleFileWriter writer =
        new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults());

    final RuleFileChange change = writer.apply(project, QualityPreset.STANDARD, gates);
    assertThat(Files.readString(root.resolve("config/enforcer-rules.xml")))
        .contains("managed-by: ward", "dependencyConvergence")
        .doesNotContain("requireMavenVersion", "banDuplicatePomDependencyVersions");
    writer.rollback(change);
    assertThat(root.resolve("config")).doesNotExist();
  }

  @Test
  void preservesUnmarkedFilesAndReplacesMarkedFiles(@TempDir final Path root) throws Exception {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final Path config = root.resolve("config");
    Files.createDirectories(config);
    final Path enforcer = config.resolve("enforcer-rules.xml");
    Files.writeString(enforcer, "user-owned");
    final PresetRuleFileWriter writer =
        new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults());
    final RuleFileChange change =
        writer.apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            QualityPreset.STRICT,
            List.of(gate("enforcer")));
    assertThat(Files.readString(enforcer)).isEqualTo("user-owned");
    assertThat(change.preserved()).containsExactly(enforcer);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
