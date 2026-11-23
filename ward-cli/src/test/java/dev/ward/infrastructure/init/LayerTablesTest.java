package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The layer boundaries each toolchain gives init (#710). */
class LayerTablesTest {
  private static Project project(final Language language, final BuildTool tool) {
    return new Project(Path.of("."), language, tool, Framework.NONE);
  }

  @Test
  void rustAndCSharpGiveNoLayerBoundaryAndMavenKeepsTheControllerOne() {
    final var tables = ArchitectureTablesFixtures.create();

    assertThat(tables.layers(project(Language.RUST, BuildTool.CARGO), "layered")).isEmpty();
    assertThat(tables.layers(project(Language.CSHARP, BuildTool.DOTNET), "hexagonal")).isEmpty();
    assertThat(tables.layers(project(Language.JAVA, BuildTool.MAVEN), "layered")).hasSize(1);
  }
}
