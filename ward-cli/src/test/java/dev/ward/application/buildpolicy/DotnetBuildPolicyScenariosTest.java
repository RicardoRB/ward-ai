package dev.ward.application.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.buildpolicy.BuildRule;
import dev.ward.domain.gate.Finding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotnetBuildPolicyScenariosTest {
  @TempDir Path root;

  private List<Finding> run(final BuildRule rule) {
    return BuildPolicyFixtures.service()
        .run(
            BuildPolicyFixtures.project(root),
            "policy",
            List.of(new BuildPolicySetting(rule, false, Map.of()))
        );
  }

  private void write(final String name, final String text) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  @Test
  @DisplayName("a dynamic version")
  void aDynamicVersion() throws IOException {
    write(
        "App.csproj",
        "<Project>\n<ItemGroup>\n<PackageReference Include=\"Serilog\" Version=\"1.*\" />\n"
            + "</ItemGroup>\n</Project>\n"
    );
    final List<Finding> findings = run(BuildRule.NO_DYNAMIC_VERSION);
    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().message()).contains("Serilog").contains("1.*");
    assertThat(findings.getFirst().file()).isEqualTo("App.csproj");
    assertThat(findings.getFirst().line()).isEqualTo(3);
  }

  @Test
  @DisplayName("a duplicate declaration")
  void aDuplicateDeclaration() throws IOException {
    write(
        "App.csproj",
        "<Project>\n<ItemGroup>\n<PackageReference Include=\"Serilog\" Version=\"3.0.0\" />\n"
            + "<PackageReference Include=\"Serilog\" Version=\"3.0.0\" />\n</ItemGroup>\n</Project>\n"
    );
    final List<Finding> findings = run(BuildRule.DUPLICATE_DEPENDENCY_DECLARATION);
    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().line()).isEqualTo(4);
    assertThat(findings.getFirst().message()).contains("line 3");
  }

  @Test
  void centralPackageVersionsAreCheckedInDirectoryPackagesProps() throws IOException {
    write(
        "Directory.Packages.props",
        "<Project>\n<ItemGroup>\n<PackageVersion Include=\"Serilog\" Version=\"3.*\" />\n"
            + "</ItemGroup>\n</Project>\n"
    );
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION))
        .extracting(Finding::file, Finding::line)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Directory.Packages.props", 3));
  }

  @Test
  void projectsListedInASolutionAreRead() throws IOException {
    write("App.sln", "Project(\"{FAE}\") = \"Lib\", \"src\\Lib\\Lib.csproj\", \"{1}\"\n");
    write(
        "src/Lib/Lib.csproj",
        "<Project><ItemGroup><PackageReference Include=\"X\" Version=\"*\" /></ItemGroup></Project>"
    );
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION))
        .extracting(Finding::file)
        .containsExactly("src/Lib/Lib.csproj");
  }

  @Test
  void aRangeIsAcceptedWhenALockFileSitsNextToTheProject() throws IOException {
    write(
        "App.csproj",
        "<Project><ItemGroup><PackageReference Include=\"X\" Version=\"[1.0,2.0)\" />"
            + "</ItemGroup></Project>"
    );
    write("packages.lock.json", "{}");
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION)).isEmpty();
  }
}
