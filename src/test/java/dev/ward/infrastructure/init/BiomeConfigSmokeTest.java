package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a real Biome on the generated {@code biome.jsonc}. Needs a local install: {@code ./mvnw
 * -Dtest=BiomeConfigSmokeTest -Dward.biome.binary=/path/to/node_modules/.bin/biome test}.
 */
@EnabledIfSystemProperty(named = "ward.biome.binary", matches = ".+")
class BiomeConfigSmokeTest {
  private static final long TIMEOUT_SECONDS = 120;
  private static final String CLEAN_SOURCE = "export const value = 1;\n";
  // Ward's coverage output is unformatted JavaScript, which Git ignores and Biome must skip.
  private static final String UNFORMATTED_REPORT = "var   report=1\n";
  private static final String FLOATING_PROMISE = "Promise.resolve(1);\n";
  private static final String FLOATING_RULE = "noFloatingPromises";
  private static final String ANY_SOURCE = "export const value: any = 1;\n";
  private static final String BASE_EXCLUDING_GENERATED =
      """
      {
        "files": { "includes": ["**", "!**/generated"] },
        "formatter": { "indentStyle": "space" },
        "vcs": { "enabled": true, "clientKind": "git", "useIgnoreFile": true },
        "linter": {
          "enabled": true,
          "rules": { "recommended": true, "suspicious": { "noExplicitAny": "off" } }
        }
      }
      """;

  @TempDir Path work;

  @Test
  void standardConfigIsFormattedAndSkipsWardReports() throws Exception {
    final Path project = project("standard", QualityPreset.STANDARD, CLEAN_SOURCE);

    final Result result = run(project, false);

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.output()).doesNotContain("source=\"format\"").doesNotContain(".ward/");
  }

  @Test
  void strictConfigIsFormattedAndSkipsWardReports() throws Exception {
    final Path project = project("strict-clean", QualityPreset.STRICT, CLEAN_SOURCE);

    final Result result = run(project, true);

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.output()).doesNotContain("source=\"format\"").doesNotContain(".ward/");
  }

  @Test
  void strictConfigFailsAFloatingPromise() throws Exception {
    final Path project = project("strict-floating", QualityPreset.STRICT, FLOATING_PROMISE);

    final Result result = run(project, true);

    assertThat(result.exit()).as(result.output()).isNotZero();
    assertThat(result.output()).contains(FLOATING_RULE);
  }

  @Test
  void strictLayerFailsAnyAndSkipsTheBasesExcludedDirectory() throws Exception {
    final Path project = layeredProject("strict-layered");

    final Result result = run(project, true);

    assertThat(result.exit()).as(result.output()).isNotZero();
    assertThat(result.output())
        .contains("noExplicitAny", "src/app.ts")
        .doesNotContain("generated/")
        .doesNotContain("source=\"format\"");
  }

  /**
   * Writes a base config that turns {@code noExplicitAny} off and excludes {@code generated/}, and
   * the strict layer over it, with an {@code any} in both directories.
   */
  private Path layeredProject(final String name) throws IOException, InterruptedException {
    final Path root = Files.createDirectories(work.resolve(name));
    git(root, "init", "-q");
    Files.writeString(root.resolve(".gitignore"), ".ward/reports/\n");
    Files.createDirectories(root.resolve("src"));
    Files.createDirectories(root.resolve("generated"));
    Files.writeString(root.resolve("src/app.ts"), ANY_SOURCE);
    Files.writeString(root.resolve("generated/gen.ts"), ANY_SOURCE);
    Files.writeString(root.resolve("biome.base.json"), BASE_EXCLUDING_GENERATED);
    Files.writeString(
        root.resolve("biome.jsonc"),
        "// managed-by: ward preset=strict\n"
            + NodeLayerTemplates.biome(QualityPreset.STRICT, "biome.base.json"));
    return root;
  }

  /**
   * Writes the generated config for {@code preset} and a source file into a Git repository whose
   * ignore file lists Ward's reports.
   */
  private Path project(final String name, final QualityPreset preset, final String source)
      throws IOException, InterruptedException {
    final Path root = Files.createDirectories(work.resolve(name));
    git(root, "init", "-q");
    Files.writeString(root.resolve(".gitignore"), ".ward/reports/\n");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.ts"), source);
    final Path reports = Files.createDirectories(root.resolve(".ward/reports/coverage"));
    Files.writeString(reports.resolve("sorter.js"), UNFORMATTED_REPORT);
    for (final Map.Entry<Path, String> target : configs(root, preset).entrySet()) {
      Files.writeString(target.getKey(), target.getValue());
    }
    return root;
  }

  /** The configs init writes for a Biome project, from the same code path init uses. */
  private static Map<Path, String> configs(final Path root, final QualityPreset preset)
      throws IOException {
    final Project project =
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE);
    final InitGateOption biome =
        new InitGateOption("biome", "Biome", List.of("npx", "biome"), true, "ok");
    return NodeConfigFiles.targets(
            project,
            PackageManifest.EMPTY,
            (directory, gate) -> Optional.empty(),
            preset,
            List.of(biome))
        .stream()
        .filter(target -> target.content() != null)
        .collect(
            Collectors.toMap(
                NodeConfigTarget::file,
                NodeConfigTarget::content,
                (first, second) -> first,
                LinkedHashMap::new));
  }

  private static void git(final Path root, final String... arguments)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(arguments));
    final Process process = new ProcessBuilder(command).directory(root.toFile()).start();
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).isZero();
  }

  /** Runs the Biome CI command Ward's gate uses, reading its checkstyle report from stdout. */
  private static Result run(final Path project, final boolean strict)
      throws IOException, InterruptedException {
    final Path log = project.resolve("biome.log");
    final List<String> command =
        new ArrayList<>(
            List.of(
                System.getProperty("ward.biome.binary"),
                "ci",
                "--colors=off",
                "--reporter=checkstyle"));
    if (strict) {
      command.add("--error-on-warnings");
    }
    command.add(".");
    final Process process =
        new ProcessBuilder(command)
            .directory(project.toFile())
            .redirectOutput(log.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
  }

  /**
   * One Biome run.
   *
   * @param exit the exit code
   * @param output the checkstyle report on standard output
   */
  private record Result(int exit, String output) {}
}
