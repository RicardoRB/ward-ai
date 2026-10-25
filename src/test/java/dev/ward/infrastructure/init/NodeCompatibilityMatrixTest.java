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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs each Node compatibility matrix cell through the real CLI: {@code init}, the clean {@code
 * check}, then the seeded violations, which must make every selected gate except {@code stryker}
 * fail. Runs only for the cells of the Node major version named by {@code ward.node.matrix}, with
 * the Ward jar named by {@code ward.node.matrix.jar}.
 */
@EnabledIfSystemProperty(named = NodeCompatibilityMatrixTest.NODE_PROPERTY, matches = "\\d+")
class NodeCompatibilityMatrixTest {
  static final String NODE_PROPERTY = "ward.node.matrix";
  private static final String JAR_PROPERTY = "ward.node.matrix.jar";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/node-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/node-matrix/violations");
  private static final String STRYKER = "stryker";
  private static final String BIOME = "biome";
  private static final String PACKAGE_JSON = "package.json";
  // Test sources skip annotation processing, so the matrix reads each check's name and status pair
  // from the compact JSON report instead of decoding it into a DTO.
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\",\"status\":\"([A-Z]+)\"");

  @ParameterizedTest(name = "node {0} {1} {2} {3}")
  @CsvFileSource(resources = "/node-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final String node,
      final String fixture,
      final String linting,
      final String preset,
      @TempDir final Path project)
      throws IOException, InterruptedException {
    Assumptions.assumeTrue(
        node.equals(System.getProperty(NODE_PROPERTY)), "row is for Node " + node);
    final String runtime = runtimeMajor();
    assertThat(runtime)
        .as("Wrong runtime: the cell needs Node %s, node --version reports %s", node, runtime)
        .isEqualTo(node);

    copyTree(FIXTURES.resolve(fixture), project);
    if (BIOME.equals(linting)) {
      declareBiome(project);
    }
    Files.writeString(project.resolve(".nvmrc"), node + "\n");
    run(project, "git", "init", "-q");
    final CommandRun init = ward(project, "init", "--preset", preset, "--yes");
    assertThat(init.exitCode()).as("ward init exit code").isZero();

    final CommandRun cleanCheck = check(project);
    assertThat(cleanCheck.exitCode()).as("clean check exit code").isZero();
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

  private static String runtimeMajor() throws IOException, InterruptedException {
    final String version = run(Path.of("."), "node", "--version").stdout().trim();
    return version.replaceFirst("^v", "").split("\\.")[0];
  }

  /** Declares Biome at the version Ward pins, so the cell's init selects the biome gate. */
  private static void declareBiome(final Path project) throws IOException {
    final Path file = project.resolve(PACKAGE_JSON);
    final String edited =
        Files.readString(file)
            .replaceFirst(
                "\\n\\}\\s*$",
                ",\n  \"devDependencies\": {\n    \"@biomejs/biome\": \""
                    + NodePackageCatalog.BIOME_VERSION
                    + "\"\n  }\n}\n");
    Files.writeString(file, edited);
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
