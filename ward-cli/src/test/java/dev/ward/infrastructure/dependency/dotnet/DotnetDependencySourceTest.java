package dev.ward.infrastructure.dependency.dotnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DotnetDependencySourceTest {
  private static final String CSPROJ_WITH_PACKAGE =
      "<Project Sdk=\"Microsoft.NET.Sdk\"><ItemGroup>"
          + "<PackageReference Include=\"Serilog\" Version=\"4.0.0\" /></ItemGroup></Project>";

  static DependencyInventory read(final Path dir) {
    return new DotnetDependencySource(new ForyJsonCodec())
        .read(new Project(dir.toAbsolutePath(), Language.CSHARP, BuildTool.DOTNET, Framework.NONE));
  }

  @ParameterizedTest
  @ValueSource(strings = {"dotnet-lock", "dotnet-assets"})
  @DisplayName("the inventory equals the package manager")
  void theInventoryEqualsThePackageManager(final String fixture) throws IOException {
    final Path dir = InventoryFixtures.fixture(fixture);
    final Set<String> listed =
        new TreeSet<>(Files.readAllLines(dir.resolve("listing.txt")));

    final Set<String> inventory =
        read(dir).distinct().stream()
            .peek(id -> assertThat(id.ecosystem()).isEqualTo(Ecosystem.NUGET))
            .map(id -> id.name() + "@" + id.version())
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(inventory).isEqualTo(listed);
    InventoryFixtures.assertMatches(read(dir), dir, "expected.txt");
  }

  @Test
  void aLockfileMarksTopLevelPackagesDirect() {
    final Dependency serilog =
        read(InventoryFixtures.fixture("dotnet-lock")).modules().getFirst().dependencies().stream()
            .filter(entry -> entry.id().name().equals("Serilog"))
            .findFirst()
            .orElseThrow();

    assertThat(serilog.direct()).isFalse();
    assertThat(serilog.requiredBy()).hasSize(1);
  }

  @Test
  void aTestProjectHasTestScope(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("T.csproj"),
        "<Project><PropertyGroup><IsTestProject>true</IsTestProject></PropertyGroup>"
            + "<ItemGroup><PackageReference Include=\"Serilog\" Version=\"4.0.0\" /></ItemGroup></Project>"
    );
    Files.writeString(
        dir.resolve("packages.lock.json"),
        "{\"version\":1,\"dependencies\":{\"net9.0\":{\"Serilog\":{\"type\":\"Direct\","
            + "\"requested\":\"[4.0.0, )\",\"resolved\":\"4.0.0\"}}}}"
    );

    assertThat(read(dir).modules().getFirst().dependencies().getFirst().scope())
        .isEqualTo(DependencyScope.TEST);
  }

  @Test
  void aSolutionOwnsOneModulePerListedProject(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("S.sln"), "Project(\"{x}\") = \"A\", \"A\\A.csproj\", \"{y}\"");
    Files.createDirectories(dir.resolve("A"));
    Files.writeString(dir.resolve("A/A.csproj"), "<Project />");
    Files.writeString(
        dir.resolve("A/packages.lock.json"),
        "{\"version\":1,\"dependencies\":{\"net9.0\":{\"Serilog\":{\"type\":\"Direct\","
            + "\"requested\":\"[4.0.0, )\",\"resolved\":\"4.0.0\"}}}}"
    );

    final DependencyInventory inventory = read(dir);

    assertThat(inventory.modules()).extracting("modulePath").containsExactly("A");
    assertThat(inventory.entryCount()).isEqualTo(1);
  }

  @Test
  void projectReferencesAreNotPackages(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("A.csproj"), "<Project />");
    Files.writeString(
        dir.resolve("packages.lock.json"),
        "{\"version\":1,\"dependencies\":{\"net9.0\":{\"Lib\":{\"type\":\"Project\"}}}}"
    );

    assertThat(read(dir).entryCount()).isZero();
  }

  @Test
  void aProjectWithoutPackagesIsEmpty(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("A.csproj"), "<Project />");

    assertThat(read(dir).entryCount()).isZero();
  }

  @Test
  void declaredPackagesWithoutALockfileNameTheRestoreCommand(@TempDir final Path dir)
      throws IOException {
    Files.writeString(dir.resolve("A.csproj"), CSPROJ_WITH_PACKAGE);

    assertThatThrownBy(() -> read(dir))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("dotnet restore")
        .hasMessageContaining("A.csproj");
  }

  @Test
  void anUnreadableLockfileNamesTheFile(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("A.csproj"), CSPROJ_WITH_PACKAGE);
    Files.writeString(dir.resolve("packages.lock.json"), "[]");

    assertThatThrownBy(() -> read(dir))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("packages.lock.json");
  }
}
