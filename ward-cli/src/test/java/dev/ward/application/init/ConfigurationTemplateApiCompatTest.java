package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTemplateApiCompatTest {
  @TempDir Path dir;

  @Test
  void nativeApiCompatIsWrittenWithUsesAndNoCommandAndReadsBack() throws IOException {
    final Project project = new Project(dir, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final String text =
        ConfigurationTemplate.render(
            List.of(new InitGateOption("api-compat", "api-compat", List.of(), true, "")),
            null,
            project);

    assertThat(text)
        .contains("[checks.api-compat]", "uses = \"api-compat\"")
        .doesNotContain("command");
    Files.writeString(dir.resolve("ward.toml"), text);
    final GateConfiguration gate =
        new TomlConfigurationReader().read(dir.resolve("ward.toml")).checks().get("api-compat");
    assertThat(gate.type()).isEqualTo("api-compat");
    assertThat(gate.command()).isEmpty();
  }
}
