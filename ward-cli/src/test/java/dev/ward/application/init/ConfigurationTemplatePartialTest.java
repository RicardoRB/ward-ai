package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.PartialGateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code partial} tables init writes, read back through the real configuration parser. */
class ConfigurationTemplatePartialTest {

  @TempDir Path dir;

  @Test
  void semgrepPassesEachPathAsItsOwnArgument() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, QualityPreset.STANDARD);

    assertThat(gates.get("semgrep").partial())
        .isEqualTo(
            new PartialGateConfiguration(
                List.of("**"), List.of(".semgrep*"), List.of("{paths}"), null, null));
  }

  @Test
  void gitleaksScansEachEditedFileOutsideGitHistory() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, QualityPreset.STANDARD);

    assertThat(gates.get("gitleaks").partial())
        .isEqualTo(
            new PartialGateConfiguration(
                List.of("**"),
                List.of(".gitleaks*"),
                List.of("--no-git", "--source={paths}"),
                null,
                null));
  }

  @Test
  void gatesThatDependOnTheWholeProjectHaveNoPartialTable() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, QualityPreset.STANDARD);

    for (final String gate : List.of("compile", "tests", "coverage", "license", "mutation")) {
      assertThat(gates.get(gate).partial()).as(gate).isNull();
    }
  }

  private Map<String, GateConfiguration> parse(
      final BuildTool buildTool, final QualityPreset preset) throws IOException {
    final List<InitGateOption> options =
        List.of("compile", "tests", "coverage", "license", "mutation", "gitleaks", "semgrep")
            .stream()
            .map(id -> new InitGateOption(id, id, List.of("tool", id), true, "available"))
            .toList();
    final Path file = dir.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(
        file,
        ConfigurationTemplate.render(
            options, preset, new Project(dir, Language.JAVA, buildTool, Framework.NONE)));
    return new TomlConfigurationReader().read(file).checks();
  }
}
