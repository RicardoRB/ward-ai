package dev.ward.infrastructure.dependency.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.RequestedVersion;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MavenGraphToolTest {
  private static final Path DIR = InventoryFixtures.fixture("maven-reactor").toAbsolutePath();

  private static Project project(final boolean wrapper) {
    return new Project(DIR, Language.JAVA, BuildTool.MAVEN, Framework.NONE, wrapper);
  }

  private static FakeCommandExecutor ok() throws IOException {
    return new FakeCommandExecutor(0, Files.readString(DIR.resolve("mvn-output.txt")), "");
  }

  @Test
  void theReactorMatchesItsGoldenInventory() throws IOException {
    final DependencyInventory inventory = new MavenGraphTool(ok(), false).resolve(project(false));

    InventoryFixtures.assertMatches(inventory, DIR, "expected.txt");
  }

  @Test
  void siblingModulesAreNotEntriesButTheirPackagesAre() throws IOException {
    final DependencyInventory inventory = new MavenGraphTool(ok(), false).resolve(project(false));

    final var api = inventory.of("api").orElseThrow();
    assertThat(api.dependencies()).noneMatch(d -> d.id().name().equals("dev.example:domain"));
    assertThat(api.dependencies())
        .anyMatch(d -> d.id().name().equals("com.google.guava:failureaccess"));
    assertThat(inventory.of("").orElseThrow().dependencies()).isEmpty();
  }

  @Test
  void mavenScopesMapAndTransitivesKeepMediatedScope() throws IOException {
    final var domain =
        new MavenGraphTool(ok(), false).resolve(project(false)).of("domain").orElseThrow();

    final Dependency slf4j =
        domain.dependencies().stream()
            .filter(d -> d.id().name().equals("org.slf4j:slf4j-api"))
            .findFirst()
            .orElseThrow();
    assertThat(slf4j.scope()).isEqualTo(DependencyScope.PROVIDED);
    assertThat(slf4j.relation()).isEqualTo(DependencyRelation.DIRECT);
    assertThat(slf4j.declaredIn()).isEqualTo("domain/pom.xml");
  }

  @Test
  void theUnmediatedTreeKeepsEveryRequestedVersion() throws IOException {
    final var api = new MavenGraphTool(ok(), false).resolve(project(false)).of("api").orElseThrow();

    final Dependency guava =
        api.dependencies().stream()
            .filter(d -> d.id().name().equals("com.google.guava:guava"))
            .findFirst()
            .orElseThrow();
    assertThat(guava.id().version()).isEqualTo("33.0.0-jre");
    assertThat(guava.requested())
        .containsExactlyInAnyOrder(
            RequestedVersion.byModule("33.0.0-jre"),
            new RequestedVersion("pkg:maven/dev.example/domain@1.0.0", "31.1-jre"));
  }

  @Test
  void theCommandIsAnArgumentVectorThatStartsWithTheWrapper() throws IOException {
    final FakeCommandExecutor executor = ok();

    new MavenGraphTool(executor, false).resolve(project(true));

    assertThat(executor.commands.getFirst())
        .containsExactly(
            "./mvnw",
            "--batch-mode",
            "--no-transfer-progress",
            "org.apache.maven.plugins:maven-dependency-plugin:"
                + MavenGraphTool.PLUGIN_VERSION
                + ":tree",
            "-DoutputType=tgf",
            "-Dverbose");
    assertThat(executor.workingDirectories.getFirst()).isEqualTo(DIR);
  }

  @Test
  void offlineAddsTheOfflineFlag() throws IOException {
    final FakeCommandExecutor executor = ok();

    new MavenGraphTool(executor, true).resolve(project(false));

    assertThat(executor.commands.getFirst()).contains("--offline");
  }

  @Test
  void aFailingBuildNamesTheExecutableAndTheFirstErrorLine() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            1, "[INFO] x\n[ERROR] Could not resolve dependencies for api\n", "");

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("mvn")
        .hasMessageContaining("Could not resolve dependencies for api")
        .hasMessageNotContaining("-DoutputType");
  }

  @Test
  void aToolThatCannotStartSaysWhichExecutableWasNotFound() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            new CommandResult(
                -1,
                "",
                "Could not start mvn: no such file",
                Duration.ZERO,
                "PROCESS_START_FAILURE"));

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .hasMessageContaining("mvn was not found");
  }

  @Test
  void aTimeoutIsReported() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            new CommandResult(-3, "", "Timed out", Duration.ZERO, "PROCESS_TIMEOUT"));

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .hasMessageContaining("did not finish");
  }

  @Test
  void outputWithoutATreeIsAnError() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "[INFO] nothing\n", "");

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .hasMessageContaining("no dependency tree");
  }
}
