package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.gradle.GradleDependencySourceTest;
import dev.ward.infrastructure.dependency.maven.MavenDependencySource;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The graph cache, driven through the Maven source with a fake build tool. */
class ResolvingDependencySourceTest {
  private static final Path FIXTURE = InventoryFixtures.fixture("maven-reactor").toAbsolutePath();

  private static final Duration STILL_FRESH = Duration.ofHours(23);
  private static final Duration EXPIRED = Duration.ofHours(25);

  @TempDir Path work;

  private FakeCommandExecutor maven;
  private Project project;

  @BeforeEach
  void copyReactor() throws IOException {
    GradleDependencySourceTest.copy(FIXTURE, work);
    maven = new FakeCommandExecutor(0, Files.readString(FIXTURE.resolve("mvn-output.txt")), "");
    project = new Project(work, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private MavenDependencySource source(final Clock clock) {
    return new MavenDependencySource(maven, new FileGraphCache(clock), new ForyJsonCodec(), false);
  }

  private static Clock at(final Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  @Test
  void aSecondRequestWithUnchangedBuildFilesRunsTheToolOnce() {
    final MavenDependencySource source = source(Clock.systemUTC());

    final DependencyInventory first = source.read(project);
    final DependencyInventory second = source.read(project);

    assertThat(maven.commands).hasSize(1);
    assertThat(second).isEqualTo(first);
  }

  @Test
  void anEditedBuildFileRunsTheToolAgain() throws IOException {
    final MavenDependencySource source = source(Clock.systemUTC());
    source.read(project);

    Files.writeString(
        work.resolve("api/pom.xml"),
        Files.readString(work.resolve("api/pom.xml")).replace("2.18.2", "2.18.3"));
    source.read(project);

    assertThat(maven.commands).hasSize(2);
  }

  @Test
  void anEntryOlderThanTwentyFourHoursRunsTheToolAgain() {
    final Instant now = Instant.now();
    source(at(now)).read(project);

    source(at(now.plus(STILL_FRESH))).read(project);
    assertThat(maven.commands).hasSize(1);

    source(at(now.plus(EXPIRED))).read(project);
    assertThat(maven.commands).hasSize(2);
  }

  @Test
  void aCorruptEntryIsReplacedAndGivesTheInventoryOfAColdRun() throws IOException {
    final MavenDependencySource source = source(Clock.systemUTC());
    final DependencyInventory cold = source.read(project);
    final Path file = onlyCacheFile();

    Files.writeString(file, "{\"format\":1,\"modules\":[{\"path\":");
    final DependencyInventory afterCorruption = source.read(project);

    assertThat(afterCorruption).isEqualTo(cold);
    assertThat(maven.commands).hasSize(2);
    assertThat(source.read(project)).isEqualTo(cold);
    assertThat(maven.commands).hasSize(2);
  }

  @Test
  void anEntryOfAnotherFormatIsNotUsed() throws IOException {
    final MavenDependencySource source = source(Clock.systemUTC());
    source.read(project);

    Files.writeString(onlyCacheFile(), "{\"format\":99,\"modules\":[]}");
    source.read(project);

    assertThat(maven.commands).hasSize(2);
  }

  @Test
  void theInventoryRoundTripsThroughTheStoredDocumentIncludingRequestedVersions() {
    final MavenDependencySource source = source(Clock.systemUTC());
    final DependencyInventory cold = source.read(project);

    final DependencyInventory warm = source.read(project);

    assertThat(warm).isEqualTo(cold);
    assertThat(warm.of("api").orElseThrow().dependencies())
        .anyMatch(dependency -> !dependency.requested().isEmpty());
  }

  private Path onlyCacheFile() {
    try (Stream<Path> files = Files.list(work.resolve(".ward/cache/dependencies"))) {
      return files.filter(file -> file.toString().endsWith(".json")).findFirst().orElseThrow();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
