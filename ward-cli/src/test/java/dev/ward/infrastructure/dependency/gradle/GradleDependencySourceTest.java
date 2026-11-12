package dev.ward.infrastructure.dependency.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GradleDependencySourceTest {
  private static final Path MULTI = InventoryFixtures.fixture("gradle-multi").toAbsolutePath();
  private static final Path LOCKED = InventoryFixtures.fixture("gradle-locked").toAbsolutePath();

  /** Stands in for Gradle: copies the committed listing into the directory the command names. */
  private static final class FakeGradle implements CommandExecutor {
    private final List<List<String>> commands = new ArrayList<>();
    private final Path listing;

    FakeGradle(final Path listing) {
      this.listing = listing;
    }

    @Override
    public CommandResult execute(final List<String> command, final Path workingDirectory) {
      commands.add(command);
      final String option =
          command.stream()
              .filter(a -> a.startsWith("-Dward.dependencies.dir="))
              .findFirst()
              .orElseThrow();
      final Path output = Path.of(option.substring(option.indexOf('=') + 1));
      try (var files = Files.list(listing)) {
        for (final Path file : files.toList()) {
          Files.copy(file, output.resolve(file.getFileName()));
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return new CommandResult(0, "", "", Duration.ZERO);
    }
  }

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
    copy(MULTI, work);
    final FakeGradle gradle = new FakeGradle(MULTI.resolve("tsv"));

    final DependencyInventory inventory = read(work, gradle);

    InventoryFixtures.assertMatches(inventory, MULTI, "expected.txt");
    assertThat(gradle.commands).hasSize(1);
  }

  @Test
  void theCommandIsAnArgumentVectorWithTheInitScriptAndTheTwoOptions(@TempDir final Path work)
      throws IOException {
    copy(MULTI, work);
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
  void aFullyLockedBuildIsReadFromTheLockfileWithoutRunningGradle() {
    final FakeGradle gradle = new FakeGradle(MULTI.resolve("tsv"));

    final DependencyInventory inventory = read(LOCKED, gradle);

    InventoryFixtures.assertMatches(inventory, LOCKED, "expected.txt");
    assertThat(gradle.commands).isEmpty();
  }

  @Test
  void lockedScopesAndDirectsFollowTheConfigurationsAndTheScripts() {
    final var module = read(LOCKED, new FakeGradle(MULTI.resolve("tsv"))).modules().getFirst();

    assertThat(scope(module.dependencies(), "com.google.j2objc:j2objc-annotations"))
        .isEqualTo(DependencyScope.PROVIDED);
    assertThat(scope(module.dependencies(), "org.junit.jupiter:junit-jupiter-api"))
        .isEqualTo(DependencyScope.TEST);
    assertThat(scope(module.dependencies(), "org.mapstruct:mapstruct-processor"))
        .isEqualTo(DependencyScope.DEVELOPMENT);
    assertThat(scope(module.dependencies(), "com.google.guava:guava"))
        .isEqualTo(DependencyScope.RUNTIME);
    assertThat(module.dependencies())
        .filteredOn(Dependency::direct)
        .extracting(d -> d.id().name())
        .containsExactlyInAnyOrder(
            "com.google.guava:guava",
            "org.slf4j:slf4j-api",
            "com.google.code.findbugs:jsr305",
            "org.junit.jupiter:junit-jupiter-api",
            "org.mapstruct:mapstruct-processor");
    assertThat(module.dependencies()).allMatch(d -> d.requiredBy().isEmpty());
  }

  @Test
  void aLockfileWithoutLockAllConfigurationsIsIgnored(@TempDir final Path work) throws IOException {
    copy(LOCKED, work);
    Files.writeString(
        work.resolve("build.gradle.kts"),
        Files.readString(work.resolve("build.gradle.kts"))
            .replace("dependencyLocking { lockAllConfigurations() }", ""));
    final FakeGradle gradle = new FakeGradle(MULTI.resolve("tsv"));

    read(work, gradle);

    assertThat(gradle.commands).hasSize(1);
  }

  @Test
  void anUnresolvedDependencyNamesTheModuleAndTheDependency(@TempDir final Path work)
      throws IOException {
    copy(MULTI, work);
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
    copy(MULTI, work);
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

  private static DependencyScope scope(final List<Dependency> entries, final String name) {
    return entries.stream()
        .filter(d -> d.id().name().equals(name))
        .findFirst()
        .orElseThrow()
        .scope();
  }

  public static void copy(final Path from, final Path to) throws IOException {
    try (var walk = Files.walk(from)) {
      for (final Path source : walk.toList()) {
        final Path target = to.resolve(from.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target);
        }
      }
    }
  }
}
