package dev.ward.infrastructure.project.dotnet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.ProjectTree;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Detection of .NET projects reads build files and never runs {@code dotnet}. */
class DotnetDetectionTest {
  private static final String CSPROJ =
      "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><TargetFramework>net9.0</TargetFramework>"
          + "</PropertyGroup></Project>";

  private static final int MEMBERS = 3;

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void repository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  @DisplayName("a single C# project")
  void aSingleCSharpProject() throws IOException {
    tree.write("App.csproj", CSPROJ);
    tree.write("Program.cs", "System.Console.WriteLine(\"hi\");\n");

    final List<DiscoveredProject> found = inspection.discover(root);

    assertThat(found).hasSize(1);
    assertThat(found.getFirst().project().language()).isEqualTo(Language.CSHARP);
    assertThat(found.getFirst().project().buildTool()).isEqualTo(BuildTool.DOTNET);
    assertThat(found.getFirst().project().toolchain()).isEqualTo(Toolchain.DOTNET);
  }

  @Test
  @DisplayName("a C# workspace")
  void aCSharpWorkspace() throws IOException {
    tree.write(
        "Shop.sln",
        solution("src/Core/Core.csproj", "src/Web/Web.csproj", "tests/T.csproj")
    );
    tree.write("Directory.Packages.props", "<Project/>");
    tree.write("src/Core/Core.csproj", CSPROJ);
    tree.write("src/Web/Web.csproj", CSPROJ);
    tree.write("tests/T.csproj", CSPROJ);

    final List<DiscoveredProject> found = inspection.discover(root);

    assertThat(ProjectTree.roots(found)).containsExactly(tree.path(""));
    assertThat(found.getFirst().moduleCount()).isEqualTo(MEMBERS);
    assertThat(inspection.detect(tree.path("src/Web")).orElseThrow().root()).isEqualTo(
        tree.path("")
    );
  }

  @Test
  @DisplayName("a slnx solution lists its members")
  void aSlnxSolutionListsItsMembers() throws IOException {
    tree.write(
        "Shop.slnx",
        "<Solution><Project Path=\"A/A.csproj\" /><Project Path=\"B/B.csproj\" /></Solution>"
    );
    tree.write("A/A.csproj", CSPROJ);
    tree.write("B/B.csproj", CSPROJ);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(""));
  }

  private static String solution(final String... projects) {
    final StringBuilder text = new StringBuilder("Microsoft Visual Studio Solution File\n");
    for (final String project : projects) {
      final String name = project.substring(project.lastIndexOf('/') + 1);
      text.append("Project(\"{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}\") = \"")
          .append(name)
          .append("\", \"")
          .append(project.replace('/', '\\'))
          .append("\", \"{00000000-0000-0000-0000-000000000000}\"\nEndProject\n");
    }
    return text.toString();
  }
}
