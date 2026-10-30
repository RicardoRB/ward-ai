package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Runs a real dependency-cruiser on the generated configs and the offline fixtures. Needs a local
 * install: {@code ./mvnw -Dtest=DependencyCruiserRulesSmokeTest
 * -Dward.depcruise.binary=/path/to/node_modules/.bin/depcruise test}.
 */
@EnabledIfSystemProperty(named = "ward.depcruise.binary", matches = ".+")
class DependencyCruiserRulesSmokeTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/node-architecture");
  private static final String VIOLATIONS = "violations";
  private static final String CYCLE = "cycle";
  private static final long TIMEOUT_SECONDS = 120;
  private static final QualityPreset.ArchitectureExtras STANDARD =
      new QualityPreset.ArchitectureExtras(false, false, false);
  private static final QualityPreset.ArchitectureExtras STRICT =
      new QualityPreset.ArchitectureExtras(true, true, true);

  @TempDir Path work;

  @ParameterizedTest
  @CsvSource({
    "typescript, layered, controller-repository, layered-architecture",
    "typescript, hexagonal, domain-infrastructure, hexagonal-architecture",
    "typescript, clean, domain-infrastructure, clean-architecture",
    "javascript, layered, controller-repository, layered-architecture",
    "javascript, hexagonal, domain-infrastructure, hexagonal-architecture",
    "javascript, clean, domain-infrastructure, clean-architecture"
  })
  void allowedImportsPassAndTheStyleViolationFails(
      final String language, final String style, final String violation, final String rule)
      throws Exception {
    final Path project = copy(language, "allowed-" + style);
    generate(project, language, style, STANDARD);

    assertThat(run(project).exit()).as("allowed imports").isZero();

    copyViolation(language, violation, project);
    final Result result = run(project);
    assertThat(result.exit()).as(result.output()).isNotZero();
    assertThat(result.output()).contains(rule);
  }

  @ParameterizedTest
  @CsvSource({"typescript", "javascript"})
  void cycleFailsOnlyUnderStrict(final String language) throws Exception {
    final Path strict = copy(language, "cycle-strict");
    generate(strict, language, "layered", STRICT);
    copyViolation(language, CYCLE, strict);
    final Result strictResult = run(strict);
    assertThat(strictResult.exit()).as(strictResult.output()).isNotZero();
    assertThat(strictResult.output()).contains("no-circular");

    final Path standard = copy(language, "cycle-standard");
    generate(standard, language, "layered", STANDARD);
    copyViolation(language, CYCLE, standard);
    assertThat(run(standard).exit()).as("standard cycle").isZero();
  }

  @Test
  void typeOnlyImportFailsInTypeScript() throws Exception {
    final Path project = copy("typescript", "type-import");
    generate(project, "typescript", "hexagonal", STANDARD);
    copyViolation("typescript", "import-type", project);

    final Result result = run(project);

    assertThat(result.exit()).as(result.output()).isNotZero();
    assertThat(result.output()).contains("hexagonal-architecture");
  }

  /** Copies the base project of {@code language}, leaving out its violation fixtures. */
  private Path copy(final String language, final String name) throws IOException {
    final Path source = FIXTURES.resolve(language);
    final Path target = Files.createDirectories(work.resolve(name));
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file :
          files
              .filter(Files::isRegularFile)
              .filter(file -> !source.relativize(file).startsWith(VIOLATIONS))
              .toList()) {
        final Path destination = target.resolve(source.relativize(file).toString());
        Files.createDirectories(destination.getParent());
        Files.copy(file, destination);
      }
    }
    return target;
  }

  /** Adds the forbidden files of {@code violation} to {@code project}, at their layer paths. */
  private static void copyViolation(
      final String language, final String violation, final Path project) throws IOException {
    final Path source = FIXTURES.resolve(language).resolve(VIOLATIONS).resolve(violation);
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path destination = project.resolve(source.relativize(file).toString());
        Files.createDirectories(destination.getParent());
        Files.copy(file, destination);
      }
    }
  }

  private static void generate(
      final Path project,
      final String language,
      final String style,
      final QualityPreset.ArchitectureExtras extras) {
    new DependencyCruiserConfigGenerator(new FileSystemNodeToolConfigs())
        .apply(
            new Project(
                project,
                "typescript".equals(language) ? Language.TYPESCRIPT : Language.JAVASCRIPT,
                BuildTool.NPM,
                Framework.NONE,
                false,
                false,
                project,
                Set.of(),
                "24",
                TaskRunner.NONE),
            style,
            extras);
  }

  /** Runs the local depcruise on {@code project} with the generated config. */
  private static Result run(final Path project) throws IOException, InterruptedException {
    final Path log = project.resolve("depcruise.log");
    final Process process =
        new ProcessBuilder(
                System.getProperty("ward.depcruise.binary"),
                ".",
                "--config",
                ".dependency-cruiser.mjs",
                "--output-type",
                "err")
            .directory(project.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
  }

  /**
   * One depcruise run.
   *
   * @param exit the exit code
   * @param output stdout and stderr together
   */
  private record Result(int exit, String output) {}
}
