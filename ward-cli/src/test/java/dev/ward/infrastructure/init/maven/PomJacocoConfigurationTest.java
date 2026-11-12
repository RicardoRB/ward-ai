package dev.ward.infrastructure.init.maven;

import static org.assertj.core.api.Assertions.assertThat;

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

/** The JaCoCo plugin writes the XML report that Ward checks; the minimum is not in the pom. */
class PomJacocoConfigurationTest {
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>com.acme</groupId>"
          + "<artifactId>lib</artifactId><version>1</version></project>";

  @TempDir Path root;

  @Test
  void aCustomSelectionDeclaresTheAgentAndTheReportAndNoRules() throws IOException {
    assertWithoutRules(configure(null));
  }

  @Test
  void aPresetDeclaresTheAgentAndTheReportAndNoRules() throws IOException {
    assertWithoutRules(configure(QualityPreset.STRICT));
  }

  private static void assertWithoutRules(final String pom) {
    assertThat(pom)
        .contains(
            "jacoco-maven-plugin",
            "<goal>prepare-agent</goal>",
            "<phase>test</phase>",
            "<goal>report</goal>")
        .doesNotContain("<rules>", "<minimum>", "jacoco:check");
  }

  private String configure(final QualityPreset preset) throws IOException {
    Files.writeString(root.resolve("pom.xml"), POM);
    new PomToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of(
                new InitGateOption(
                    "coverage", "coverage", List.of("mvn", "jacoco:report"), true, "ok")),
            preset);
    return Files.readString(root.resolve("pom.xml"));
  }
}
