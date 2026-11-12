package dev.ward.infrastructure.dependency.maven;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.RequestedVersion;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    final ModuleDependencies api = inventory.of("api").orElseThrow();
    assertThat(api.dependencies()).noneMatch(d -> "dev.example:domain".equals(d.id().name()));
    assertThat(api.dependencies())
        .anyMatch(d -> "com.google.guava:failureaccess".equals(d.id().name()));
    assertThat(inventory.of("").orElseThrow().dependencies()).isEmpty();
  }

  @Test
  void mavenScopesMapAndTransitivesKeepMediatedScope() throws IOException {
    final ModuleDependencies domain =
        new MavenGraphTool(ok(), false).resolve(project(false)).of("domain").orElseThrow();

    final Dependency slf4j =
        domain.dependencies().stream()
            .filter(d -> "org.slf4j:slf4j-api".equals(d.id().name()))
            .findFirst()
            .orElseThrow();
    assertThat(slf4j.scope()).isEqualTo(DependencyScope.PROVIDED);
    assertThat(slf4j.relation()).isEqualTo(DependencyRelation.DIRECT);
    assertThat(slf4j.declaredIn()).isEqualTo("domain/pom.xml");
  }

  @Test
  void theUnmediatedTreeKeepsEveryRequestedVersion() throws IOException {
    final ModuleDependencies api =
        new MavenGraphTool(ok(), false).resolve(project(false)).of("api").orElseThrow();

    final Dependency guava =
        api.dependencies().stream()
            .filter(d -> "com.google.guava:guava".equals(d.id().name()))
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
}
