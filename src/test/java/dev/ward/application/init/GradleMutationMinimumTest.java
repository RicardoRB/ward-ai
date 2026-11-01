package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Gradle build keeps no PIT threshold, so ward.toml carries the preset's summed minimum. */
class GradleMutationMinimumTest {
  private static final int STRICT_MINIMUM = 60;
  private static final InitGateOption MUTATION =
      new InitGateOption("mutation", List.of("./gradlew", "pitest"), true, "available");

  @TempDir Path root;

  @Test
  void strictGradleBuildWritesThePresetMinimumForTheMutationGate() throws IOException {
    assertThat(minimumOf(BuildTool.GRADLE, QualityPreset.STRICT)).isEqualTo(STRICT_MINIMUM);
  }

  @Test
  void mavenBuildKeepsItsThresholdInThePomWithNoMinimumInWardToml() throws IOException {
    assertThat(minimumOf(BuildTool.MAVEN, QualityPreset.STRICT)).isNull();
  }

  @Test
  void standardGradleBuildSetsNoMinimum() throws IOException {
    assertThat(minimumOf(BuildTool.GRADLE, QualityPreset.STANDARD)).isNull();
  }

  private Integer minimumOf(final BuildTool tool, final QualityPreset preset) throws IOException {
    final Project project = new Project(root, Language.JAVA, tool, Framework.NONE);
    final Path file = root.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(file, ConfigurationTemplate.render(List.of(MUTATION), preset, project));
    return new TomlConfigurationReader().read(file).checks().get("mutation").minimum();
  }
}
