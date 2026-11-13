package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitSelection;
import dev.ward.infrastructure.init.ArchitectureTablesFixtures;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Selecting crap without its coverage data gate fails before init writes any file. */
class InitCrapRejectionTest {
  private final InitService service =
      new InitService(
          new ProjectDetector(new FileSystemProjectInspection()),
          Set.of(),
          new InitSetupCatalog(new FakeEnvironmentInspection()),
          new InitProjectFiles(
              new PomToolConfigurator(),
              new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults()),
              ArchitectureTablesFixtures.create(),
              new FileConfigurationStorage(),
              new OwnedSectionWriter()));

  @Test
  void writesNothingWhenCoverageIsMissing(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final String pom = Files.readString(dir.resolve("pom.xml"));

    assertThatThrownBy(
            () ->
                service.initialize(
                    dir,
                    new InitSelection(List.of("none"), List.of("compile", "crap"), null, null)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("crap")
        .hasMessageContaining("coverage");

    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.map(file -> file.getFileName().toString())).containsExactly("pom.xml");
    }
    assertThat(Files.readString(dir.resolve("pom.xml"))).isEqualTo(pom);
  }
}
