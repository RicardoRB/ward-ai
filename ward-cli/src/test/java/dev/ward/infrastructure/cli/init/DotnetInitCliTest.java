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

  private InitCommandFixture init() throws IOException {
    return InitCommandFixture.repository(root, new DotnetBuildToolConfigurator(), "dotnet");
  }
}
