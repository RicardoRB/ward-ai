package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.dotnet.DotnetDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The architecture gate of a C# project, from the issue's scenario (#663). */
class DotnetLayerScenarioTest {
  @TempDir Path root;

  @Test
  @DisplayName("a layer violation")
  void aLayerViolation() throws IOException {
    write(
        "src/Domain/Order.cs",
        "using Shop.Infrastructure;\nnamespace Shop.Domain;\nclass Order {}\n"
    );
    write("src/Infrastructure/Db.cs", "namespace Shop.Infrastructure;\nclass Db {}\n");

    final List<Finding> findings =
        LayerPorts.findings(
            new Project(root, Language.CSHARP, BuildTool.DOTNET, Framework.NONE),
            new LayerBoundary(List.of("Domain"), List.of("Infrastructure")),
            new ProjectDependencyAdapters(List.of(new DotnetDependencyAdapters()))
        );

    assertThat(findings).extracting(Finding::file).containsExactly("src/Domain/Order.cs");
    assertThat(findings.getFirst().message())
        .startsWith("Layer Domain must not depend on Infrastructure: ")
        .endsWith("src/Infrastructure/Db.cs");
    assertThat(findings.getFirst().line()).isEqualTo(1);
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
