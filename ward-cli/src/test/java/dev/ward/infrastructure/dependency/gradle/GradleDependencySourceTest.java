package dev.ward.infrastructure.dependency.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gradle resolution through the build tool, over committed fixtures. */
class GradleDependencySourceTest {
  private static final Path MULTI = InventoryFixtures.fixture("gradle-multi").toAbsolutePath();

  private static Project project(final Path dir, final boolean wrapper) {
    return new Project(dir, Language.JAVA, BuildTool.GRADLE, Framework.NONE, wrapper);
  }

  private static GradleDependencySource source(final CommandExecutor executor) {
    return new GradleDependencySource(
        executor,
        new dev.ward.infrastructure.dependency.FileGraphCache(),
        new ForyJsonCodec(),
        false);
  }

  private static DependencyInventory read(final Path dir, final CommandExecutor executor) {
    return source(executor).read(project(dir, false));
  }

  @Test
  void theBuildToolListingMatchesItsGoldenInventory(@TempDir final Path work) throws IOException {
    InventoryFixtures.copy(MULTI, work);
    final FakeGradle gradle = new FakeGradle(MULTI.resolve("tsv"));

    final DependencyInventory inventory = read(work, gradle);

    InventoryFixtures.assertMatches(inventory, MULTI, "expected.txt");
    assertThat(gradle.commands).hasSize(1);
  }

  @Test
  void theCommandIsAnArgumentVectorWithTheInitScriptAndTheTwoOptions(@TempDir final Path work)
      throws IOException {
    InventoryFixtures.copy(MULTI, work);
    final FakeGradle gradle = new FakeGradle(MULTI.resolve("tsv"));

    source(gradle).read(project(work, true));

    final List<String> command = gradle.commands.getFirst();
    assertThat(command.getFirst()).isEqualTo("./gradlew");
    assertThat(command)
        .contains(
            "-I", "wardDependencies", "--console=plain", "-Dorg.gradle.configuration-cache=false");
    assertThat(command).anyMatch(a -> a.startsWith("-Dward.dependencies.dir="));
    assertThat(command).noneMatch(a -> a.contains("\n"));
  }

  @Test
  void anUnresolvedDependencyNamesTheModuleAndTheDependency(@TempDir final Path work)
      throws IOException {
    InventoryFixtures.copy(MULTI, work);
    final Path listing = work.resolve("bad-listing");
    Files.createDirectories(listing);
    Files.writeString(
        listing.resolve("app.tsv"),
        "# path=app\nUNRESOLVED\tcompileClasspath\tcom.example:missing:1.0\tCould not find com.example:missing:1.0\n");
    Files.writeString(listing.resolve("root.tsv"), "# path=\n");

    assertThatThrownBy(() -> read(work, new FakeGradle(listing)))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("module app")
        .hasMessageContaining("com.example:missing:1.0");
  }

  @Test
  void aMissingGradleSaysWhichExecutableWasNotFound(@TempDir final Path work) throws IOException {
    InventoryFixtures.copy(MULTI, work);
    final CommandExecutor missing =
        (command, dir) ->
            new CommandResult(
                -1,
                "",
                "Could not start gradle: not found",
                Duration.ZERO,
                "PROCESS_START_FAILURE");

    assertThatThrownBy(() -> read(work, missing)).hasMessageContaining("gradle was not found");
  }

  @Test
  void theInitScriptResourceIsPackaged() {
    assertThat(GradleGraphTool.class.getResource(GradleGraphTool.SCRIPT_RESOURCE)).isNotNull();
  }
}
