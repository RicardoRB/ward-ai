package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs each Node compatibility matrix cell through the real CLI: {@code init}, the clean {@code
 * check}, then the seeded violations, which must make every selected gate except {@code stryker}
 * fail. Runs only for the cells whose Node value is named by {@code ward.node.matrix} (a major, or
 * a major and minor) and, when {@code ward.node.matrix.fixture} is set, whose fixture it names,
 * with the Ward jar named by {@code ward.node.matrix.jar}.
 */
@EnabledIfSystemProperty(
    named = NodeCompatibilityMatrixTest.NODE_PROPERTY,
    matches = "\\d+(\\.\\d+)?")
class NodeCompatibilityMatrixTest {
  static final String NODE_PROPERTY = "ward.node.matrix";
  private static final String FIXTURE_PROPERTY = "ward.node.matrix.fixture";
  private static final String JAR_PROPERTY = "ward.node.matrix.jar";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/node-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/node-matrix/violations");
  private static final String STRYKER = "stryker";
  private static final String BIOME = "biome";
  private static final String ESLINT_JS = "@eslint/js";
  private static final String PACKAGE_JSON = "package.json";
  private static final String ASTRO = "astro";
  private static final int PACKAGES_ARGUMENT = 5;
  // Test sources skip annotation processing, so the matrix reads each check's name and status pair
  // from the compact JSON report instead of decoding it into a DTO.
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\",\"status\":\"([A-Z]+)\"");

  @ParameterizedTest(name = "node {0} {1} {2} {3}")
  @CsvFileSource(resources = "/node-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final ArgumentsAccessor cell, @TempDir final Path project)
      throws IOException, InterruptedException {
    final String node = cell.getString(0);
    final String fixture = cell.getString(1);
    final String linting = cell.getString(2);
    final String preset = cell.getString(3);
    final String version = Objects.requireNonNullElse(cell.getString(4), "");
    // The packages column is optional: rows without it have only the first five arguments.
    final String packageSpec =
        cell.size() > PACKAGES_ARGUMENT ? cell.getString(PACKAGES_ARGUMENT) : "";
    final Map<String, String> packages = NodeMatrixFixtureEdits.parsePackages(packageSpec);
    Assumptions.assumeTrue(
        node.equals(System.getProperty(NODE_PROPERTY)), "row is for Node " + node);
    Assumptions.assumeTrue(
        fixture.equals(System.getProperty(FIXTURE_PROPERTY, fixture)),
        "row is for fixture " + fixture);
    final String runtime = runtimeVersion();
    assertThat(runtime.equals(node) || runtime.startsWith(node + "."))
        .as("Wrong runtime: the cell needs Node %s, node --version reports %s", node, runtime)
        .isTrue();

    copyTree(FIXTURES.resolve(fixture), project);
    declareLinter(project, linting, version);
    NodeMatrixFixtureEdits.declarePackages(project, packages);
    if (ASTRO.equals(fixture)) {
      NodeMatrixFixtureEdits.writeLintViolationInAstroFolder(project);
    }
    Files.writeString(project.resolve(".nvmrc"), node + "\n");
    run(project, "git", "init", "-q");
    final CommandRun init = ward(project, "init", "--preset", preset, "--yes");
    assertThat(init.exitCode()).as("ward init exit code").isZero();

    final CommandRun cleanCheck = check(project);
    assertThat(cleanCheck.exitCode())
        .as("clean check exit code; output: %s", cleanCheck.stdout())
        .isZero();
    final Map<String, String> clean = statuses(cleanCheck);
    assertThat(clean).as("selected gates").isNotEmpty();
    clean.forEach(
        (gate, status) -> assertThat(status).as("clean gate %s", gate).isEqualTo("PASSED"));

    copyTree(VIOLATIONS.resolve(fixture), project);
    final Map<String, String> seeded = statuses(check(project));
    for (final String gate : clean.keySet()) {
      if (!STRYKER.equals(gate)) {
        assertThat(seeded.get(gate))
            .as("gate that runs nothing is caught: %s", gate)
            .isEqualTo("FAILED");
      }
    }
  }

  private static CommandRun check(final Path project) throws IOException, InterruptedException {
    return ward(project, "check", "--profile", "ci", "--format", "json");
  }

  private static Map<String, String> statuses(final CommandRun check) {
    final Map<String, String> statuses = new LinkedHashMap<>();
    final Matcher matcher = CHECK.matcher(check.stdout());
    while (matcher.find()) {
      statuses.put(matcher.group(1), matcher.group(2));
    }
    return statuses;
  }

  private static CommandRun ward(final Path dir, final String... args)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(ProcessHandle.current().info().command().orElse("java"));
    command.add("-jar");
    command.add(Path.of(System.getProperty(JAR_PROPERTY)).toAbsolutePath().toString());
    command.addAll(List.of(args));
    return run(dir, command.toArray(String[]::new));
  }

  private static CommandRun run(final Path dir, final String... command)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(command)
            .directory(dir.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    final String stdout =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new CommandRun(process.waitFor(), stdout);
  }

  /** The runtime's full version without the leading {@code v}, such as {@code 22.12.0}. */
  private static String runtimeVersion() throws IOException, InterruptedException {
    return run(Path.of("."), "node", "--version").stdout().trim().replaceFirst("^v", "");
  }

  /**
   * Declares the linter at the row's version, or at the pinned version when the row has none, so
   * init keeps the declared version. ESLint 9 needs {@code @eslint/js} at its own version; ESLint
   * 10 declares no {@code @eslint/js} dependency, so it gets the pinned one.
   */
  private static void declareLinter(final Path project, final String linting, final String version)
      throws IOException {
    final boolean biome = BIOME.equals(linting);
    final String declared =
        version.isEmpty()
            ? (biome ? NodePackageCatalog.BIOME_VERSION : NodePackageCatalog.ESLINT_VERSION)
            : version;
    final Map<String, String> dependencies = new LinkedHashMap<>();
    if (biome) {
      dependencies.put(NodePackageCatalog.BIOME_PACKAGE, declared);
    } else {
      dependencies.put("eslint", declared);
      dependencies.put(ESLINT_JS, declared.startsWith("9.") ? declared : eslintJsPin());
    }
    // A framework fixture may already declare dependencies, so the linter merges into them.
    final Path file = project.resolve(PACKAGE_JSON);
    Files.writeString(
        file, PackageJsonEdit.addDevDependencies(Files.readString(file), dependencies));
  }

  private static String eslintJsPin() {
    return NodePackageCatalog.find(ESLINT_JS).orElseThrow().version();
  }

  private static void copyTree(final Path from, final Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (final Path source : files.toList()) {
        final Path target = to.resolve(from.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /** The stdout and exit status of one process. */
  record CommandRun(int exitCode, String stdout) {}
}
