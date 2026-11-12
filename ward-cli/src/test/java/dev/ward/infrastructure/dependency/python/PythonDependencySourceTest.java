package dev.ward.infrastructure.dependency.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PythonDependencySourceTest {
  private static DependencyInventory read(final Path dir, final BuildTool tool) {
    return new PythonDependencySource()
        .read(new Project(dir.toAbsolutePath(), Language.PYTHON, tool, Framework.NONE));
  }

  @ParameterizedTest
  @CsvSource({"uv-single,UV", "uv-workspace,UV", "poetry,POETRY", "pip,PIP", "pip-includes,PIP"})
  void everySourceMatchesItsGoldenInventory(final String fixture, final BuildTool tool) {
    final Path dir = InventoryFixtures.fixture(fixture);

    InventoryFixtures.assertMatches(read(dir, tool), dir, "expected.txt");
  }

  @Test
  void aTruncatedUvLockNamesTheFile(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("pyproject.toml"), "[project]\nname='a'\ndependencies=['idna']\n");
    Files.writeString(dir.resolve("uv.lock"), "version = 1\n[[package\n");

    assertThatThrownBy(() -> read(dir, BuildTool.UV))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("uv.lock");
  }

  @Test
  void aMissingLockfileNamesTheCommandThatCreatesIt(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("pyproject.toml"), "[project]\nname='a'\ndependencies=['idna']\n");

    assertThatThrownBy(() -> read(dir, BuildTool.POETRY)).hasMessageContaining("poetry lock");
    assertThatThrownBy(() -> read(dir, BuildTool.UV)).hasMessageContaining("uv lock");
  }

  @Test
  void aDeclaredDependencyMissingFromTheLockfileFails(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("pyproject.toml"), "[project]\nname='a'\ndependencies=['idna']\n");
    Files.writeString(
        dir.resolve("uv.lock"),
        "version = 1\n[[package]]\nname = \"a\"\nversion = \"0\"\nsource = { virtual = \".\" }\n");

    assertThatThrownBy(() -> read(dir, BuildTool.UV))
        .hasMessageContaining("idna")
        .hasMessageContaining("uv lock");
  }

  @Test
  void aLockVersionItDoesNotKnowFails(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("pyproject.toml"), "[project]\nname='a'\ndependencies=['idna']\n");
    Files.writeString(dir.resolve("uv.lock"), "version = 7\n");

    assertThatThrownBy(() -> read(dir, BuildTool.UV)).hasMessageContaining("version 7");
  }

  @Test
  void aProjectWithoutDependenciesNeedsNoLockfile(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("pyproject.toml"), "[project]\nname='a'\n");

    assertThat(read(dir, BuildTool.UV).entryCount()).isZero();
  }

  @Test
  void anUnpinnedRequirementNamesTheFileTheLineAndWhyItFails(@TempDir final Path dir)
      throws IOException {
    Files.writeString(dir.resolve("requirements.txt"), "idna==3.10\ndjango>=4\n");

    assertThatThrownBy(() -> read(dir, BuildTool.PIP))
        .hasMessageContaining("requirements.txt")
        .hasMessageContaining("line 2")
        .hasMessageContaining("django>=4")
        .hasMessageContaining("not an exact version");
  }

  @Test
  void editableUrlAndVcsRequirementsAreErrors(@TempDir final Path dir) throws IOException {
    for (final String line :
        new String[] {
          "-e .", "git+https://github.com/a/b.git#egg=b", "pkg @ https://x/y.whl", "./local"
        }) {
      Files.writeString(dir.resolve("requirements.txt"), line + "\n");

      assertThatThrownBy(() -> read(dir, BuildTool.PIP))
          .as(line)
          .isInstanceOf(DependencyInventoryException.class);
    }
  }

  @Test
  void anIncludeOutsideTheProjectIsAnError(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("requirements.txt"), "-r ../other.txt\n");

    assertThatThrownBy(() -> read(dir, BuildTool.PIP)).hasMessageContaining("not in the project");
  }

  @Test
  void aViaAnnotationNamingAPackageMakesTheEntryTransitive(@TempDir final Path dir)
      throws IOException {
    Files.writeString(
        dir.resolve("requirements.txt"),
        "idna==3.10\n    # via requests\nrequests==2.32.3\n    # via -r requirements.in\n");

    final var entries = read(dir, BuildTool.PIP).modules().getFirst().dependencies();

    assertThat(entries)
        .extracting(e -> e.id().name() + ":" + e.relation())
        .containsExactly("idna:TRANSITIVE", "requests:DIRECT");
    assertThat(entries).allMatch(e -> e.requiredBy().isEmpty());
  }
}
