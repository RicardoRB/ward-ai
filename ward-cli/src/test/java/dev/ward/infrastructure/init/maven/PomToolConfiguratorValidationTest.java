package dev.ward.infrastructure.init.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
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

class PomToolConfiguratorValidationTest {

  /** Well-formed apart from an undefined entity, so only the pre-write check can reject it. */
  private static final String UNDEFINED_ENTITY_POM =
      "<project>\n  <name>&undefined;</name>\n  <build>\n    <plugins>\n    </plugins>\n"
          + "  </build>\n</project>\n";

  @Test
  void leavesPomUnchangedAndNamesToolWhenValidationFails(@TempDir final Path root)
      throws IOException {
    final Path pom = root.resolve("pom.xml");
    Files.writeString(pom, UNDEFINED_ENTITY_POM);
    final byte[] original = Files.readAllBytes(pom);

    assertThatThrownBy(
            () -> new PomToolConfigurator().apply(project(root), List.of(gate("coverage"))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("while adding jacoco-maven-plugin")
        .hasMessageContaining("not well-formed XML (line 2");
    assertThat(Files.readAllBytes(pom)).isEqualTo(original);
  }

  @Test
  void writesValidatedPomWhenChecksPass(@TempDir final Path root) throws IOException {
    final Path pom = root.resolve("pom.xml");
    Files.writeString(pom, "<project>\n  <build/>\n</project>\n");

    new PomToolConfigurator().apply(project(root), List.of(gate("coverage"), gate("mutation")));

    assertThat(Files.readString(pom))
        .contains("<artifactId>jacoco-maven-plugin</artifactId>")
        .contains("<artifactId>pitest-maven</artifactId>");
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
