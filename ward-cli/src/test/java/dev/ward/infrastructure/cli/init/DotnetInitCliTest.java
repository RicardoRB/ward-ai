package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.dotnet.DotnetBuildToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@code ward init} on .NET projects and checks what init writes. */
class DotnetInitCliTest {
  private static final String CSPROJ = "<Project Sdk=\"Microsoft.NET.Sdk\"/>";

  @TempDir Path root;

  @Test
  @DisplayName("a single C# project")
  void aSingleCSharpProject() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    final InitCommandFixture init = init();

    final int exit = init.run("", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml).contains("dotnet-build", "dotnet-test").doesNotContain("mvn", "swift-");
    assertThat(Files.readString(root.resolve("App.csproj"))).isEqualTo(CSPROJ);
  }

  @Test
  @DisplayName("a C# workspace")
  void aCSharpWorkspace() throws IOException {
    Files.writeString(
        root.resolve("Shop.slnx"),
        "<Solution><Project Path=\"A/A.csproj\"/><Project Path=\"B/B.csproj\"/>"
            + "<Project Path=\"C/C.csproj\"/></Solution>"
    );
    Files.writeString(root.resolve("Directory.Packages.props"), "<Project/>");
    for (final String name : new String[] {"A", "B", "C"}) {
      Files.createDirectories(root.resolve(name));
      Files.writeString(root.resolve(name).resolve(name + ".csproj"), CSPROJ);
    }
    final InitCommandFixture init = init();

    final int exit = init.run("", "--preset", "strict", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml).contains("dotnet-build", "dotnet-test", "-warnaserror");
    assertThat(root.resolve("A/ward.toml")).doesNotExist();
  }

  @Test
  @DisplayName("no controller and repository layer rule, which no C# layout needs (#710)")
  void noControllerAndRepositoryLayerRule() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    final InitCommandFixture init = init();

    final int exit = init.run("", "--preset", "strict", "--architecture", "layered", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .doesNotContain("[[checks.ward.layers]]", "controller", "repository");
    assertThat(Files.readString(root.resolve("ward-architecture.toml")))
        .doesNotContain("lower-layers-do-not-call-controllers")
        .contains("version = 1");
  }

  @Test
  @DisplayName("tests with coverage and mutation testing")
  void testsWithCoverageAndMutationTesting() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    final InitCommandFixture init = init();

    final int exit = init.run("", "--preset", "strict", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml)
        .contains(
            "'--collect:XPlat Code Coverage'",
            "report = \".ward/reports/dotnet/*/coverage.cobertura.xml\"",
            "minimum-branches = 75",
            "dotnet stryker --reporter json --output .ward/stryker"
        );
    assertThat(Files.readString(root.resolve(".config/dotnet-tools.json")))
        .contains("dotnet-stryker");
  }

  @Test
  @DisplayName("the ward gate selects the C# rules")
  void theWardGateSelectsTheCSharpRules() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    final InitCommandFixture init = init();

    final int exit = init.run("", "--preset", "strict", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward-rules.toml")))
        .contains("preset = \"strict\"", "languages = [\"csharp\", \"swift\"]")
        .doesNotContain("\"java\"");
    assertThat(init.output())
        .contains("Updated .config/dotnet-tools.json with .NET tools:")
        .doesNotContain("pom.xml");
  }

  @Test
  @DisplayName("build output is ignored")
  void buildOutputIsIgnored() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    final InitCommandFixture init = init();

    final int exit = init.run("", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve(".gitignore")))
        .contains("bin/", "obj/", ".ward/reports/", ".ward/stryker/");
  }

  @Test
  @DisplayName("standard has no mutation gate")
  void standardHasNoMutationGate() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    final InitCommandFixture init = init();

    final int exit = init.run("", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("minimum = 80")
        .doesNotContain("stryker");
    assertThat(root.resolve(".config/dotnet-tools.json")).doesNotExist();
  }

  @Test
  @DisplayName("an existing tool manifest stays")
  void anExistingToolManifestStays() throws IOException {
    Files.writeString(root.resolve("App.csproj"), CSPROJ);
    Files.writeString(root.resolve("Program.cs"), "System.Console.WriteLine(1);\n");
    Files.createDirectories(root.resolve(".config"));
    final String own = "{\"version\":1,\"isRoot\":true,\"tools\":{}}";
    Files.writeString(root.resolve(".config/dotnet-tools.json"), own);
    final InitCommandFixture init = init();

    final int exit = init.run("", "--preset", "strict", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve(".config/dotnet-tools.json"))).isEqualTo(own);
  }

  private InitCommandFixture init() throws IOException {
    return InitCommandFixture.repository(root, new DotnetBuildToolConfigurator(), "dotnet");
  }
}
