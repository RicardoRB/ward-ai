package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each member of a mixed root gets its own {@code [checks.*.paths]} tables, as init writes them.
 */
class MixedMultiProjectPartialTest {
  private static final String ESLINT = "eslint";
  private static final String CHECKSTYLE = "checkstyle";

  @TempDir Path root;

  @Test
  void nodeAndJvmMembersKeepTheirOwnPartialTables() throws IOException {
    final Path web = Files.createDirectories(root.resolve("web"));
    final Path api = Files.createDirectories(root.resolve("api"));
    final Project nodeMember =
        new Project(
            web,
            Language.TYPESCRIPT,
            BuildTool.PNPM,
            Framework.NONE,
            false,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    final Project jvmMember = new Project(api, Language.JAVA, BuildTool.MAVEN, Framework.NONE);

    final Map<String, GateConfiguration> node =
        write(nodeMember, List.of(ESLINT), QualityPreset.STANDARD);
    final Map<String, GateConfiguration> jvm =
        write(jvmMember, List.of(CHECKSTYLE), QualityPreset.STANDARD);

    assertThat(node.get(ESLINT).partial().match())
        .contains("**/*.ts", "**/*.tsx", "**/*.js")
        .doesNotContain("src/**/*.java");
    assertThat(jvm.get(CHECKSTYLE).partial().match()).containsExactly("src/**/*.java");
    assertThat(jvm.get(CHECKSTYLE).partial().args())
        .containsExactly("-Dcheckstyle.includes={paths}");
    assertThat(node.get(CHECKSTYLE)).isNull();
    assertThat(jvm.get(ESLINT)).isNull();
  }

  private Map<String, GateConfiguration> write(
      final Project member, final List<String> gates, final QualityPreset preset)
      throws IOException {
    final List<InitGateOption> options =
        gates.stream()
            .map(id -> new InitGateOption(id, id, List.of("tool", id), true, "available"))
            .toList();
    final Path file = member.root().resolve(WardConfiguration.FILE_NAME);
    Files.writeString(file, ConfigurationTemplate.render(options, preset, member));
    return new TomlConfigurationReader().read(file).checks();
  }
}
