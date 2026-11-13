package dev.ward.infrastructure.dependency.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleLockSourceTest {
  private static final Path MULTI = InventoryFixtures.fixture("gradle-multi").toAbsolutePath();
  private static final Path LOCKED = InventoryFixtures.fixture("gradle-locked").toAbsolutePath();

  private static Project project(final Path dir, final boolean wrapper) {
    return new Project(dir, Language.JAVA, BuildTool.GRADLE, Framework.NONE, wrapper);
  }

  private static DependencyInventory read(final Path dir, final CommandExecutor executor) {
    return new GradleDependencySource(
            executor,
            new dev.ward.infrastructure.dependency.FileGraphCache(),
            new ForyJsonCodec(),
            false)
        .read(project(dir, false));
  }

  private static DependencyScope scope(final List<Dependency> entries, final String name) {
    return entries.stream()
        .filter(d -> d.id().name().equals(name))
        .findFirst()
        .orElseThrow()
        .scope();
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
    final ModuleDependencies module =
        read(LOCKED, new FakeGradle(MULTI.resolve("tsv"))).modules().getFirst();

    assertThat(scope(module.dependencies(), "com.google.code.findbugs:jsr305"))
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
    InventoryFixtures.copy(LOCKED, work);
    Files.writeString(
        work.resolve("build.gradle.kts"),
        Files.readString(work.resolve("build.gradle.kts"))
            .replace("dependencyLocking { lockAllConfigurations() }", ""));
    final FakeGradle gradle = new FakeGradle(MULTI.resolve("tsv"));

    read(work, gradle);

    assertThat(gradle.commands).hasSize(1);
  }
}
