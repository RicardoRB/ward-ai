package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.process.Command;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.gradle.GradleDependencySource;
import dev.ward.infrastructure.dependency.gradle.GradleDependencySourceTest;
import dev.ward.infrastructure.dependency.maven.MavenDependencySource;
import dev.ward.infrastructure.dependency.node.NodeDependencySource;
import dev.ward.infrastructure.dependency.python.PythonDependencySource;
import dev.ward.infrastructure.dependency.swift.SwiftDependencySource;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compares the inventory's set of ecosystem, name and version with the listing of the package
 * manager itself, on the fixture projects. Each test needs its package manager and the network, so
 * each runs only with {@code -Dward.dependency.parity.<tool>=true} (tools: npm, pnpm, uv, maven,
 * gradle, swift).
 */
class DependencyParitySmokeTest {
  private static final int VERSION = 3;
  private static final int MEDIATED = 4;
  private static final String LOCAL = "local:";
  private static final String PROPERTY = "ward.dependency.parity.";
  private static final JsonCodec CODEC = new ForyJsonCodec();
  private static final ProcessCommandExecutor PROCESSES = new ProcessCommandExecutor();
  private static final Pattern PYTHON_PIN = Pattern.compile("^([A-Za-z0-9_.-]+)==(\\S+)");
  private static final Pattern MAVEN_LIST =
      Pattern.compile(
          "^\\[INFO]\\s+([\\w.\\-]+):([\\w.\\-]+):[\\w\\-]+:(?:[\\w\\-]+:)?([^:\\s]+):\\w+");
  private static final Pattern GRADLE_LINE =
      Pattern.compile(
          "[+\\\\]--- ([\\w.\\-]+):([\\w.\\-]+):([^\\s]+?)(?: -> ([^\\s]+))?(?: \\(.\\))?$");

  private static void enabled(final String tool) {
    assumeTrue(Boolean.getBoolean(PROPERTY + tool), "set -D" + PROPERTY + tool + "=true");
  }

  private static String run(final Path dir, final String... arguments) {
    final CommandResult result = PROCESSES.execute(new Command(List.of(arguments), dir));
    assertThat(result.succeeded())
        .as(String.join(" ", arguments) + ": " + result.stderr())
        .isTrue();
    return result.stdout();
  }

  private static Path copy(final String fixture, final Path work) throws IOException {
    GradleDependencySourceTest.copy(InventoryFixtures.fixture(fixture).toAbsolutePath(), work);
    return work;
  }

  /** Drops the workspace members that a listing marks with {@code local:<name>}. */
  private static Set<String> withoutMembers(final Set<String> listed) {
    final Set<String> members =
        listed.stream()
            .filter(entry -> entry.startsWith(LOCAL))
            .map(entry -> entry.substring(LOCAL.length()) + "@")
            .collect(Collectors.toSet());
    return listed.stream()
        .filter(entry -> !entry.startsWith(LOCAL))
        .filter(entry -> members.stream().noneMatch(entry::startsWith))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Set<String> inventorySet(
      final DependencyInventory inventory, final Ecosystem ecosystem) {
    return inventory.distinct().stream()
        .filter(id -> id.ecosystem() == ecosystem)
        .map(id -> id.name() + "@" + id.version())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Test
  void npmMatchesNpmLsAll(@TempDir final Path work) throws IOException {
    enabled("npm");
    final Path dir = copy("npm-workspace", work);
    run(dir, "npm", "install", "--ignore-scripts", "--no-audit", "--no-fund");
    final Set<String> listed = new TreeSet<>();
    collectNpm(CODEC.fromJson(run(dir, "npm", "ls", "--all", "--json"), Object.class), listed);

    final DependencyInventory inventory =
        new NodeDependencySource(CODEC)
            .read(new Project(dir, Language.JAVASCRIPT, BuildTool.NPM, Framework.NONE));

    assertThat(inventorySet(inventory, Ecosystem.NPM)).isEqualTo(withoutMembers(listed));
  }

  @Test
  void pnpmMatchesPnpmLsRecursive(@TempDir final Path work) throws IOException {
    enabled("pnpm");
    final Path dir = copy("pnpm-workspace", work);
    run(dir, "pnpm", "install", "--ignore-scripts");
    final Set<String> listed = new TreeSet<>();
    final Object projects =
        CODEC.fromJson(
            run(dir, "pnpm", "ls", "--recursive", "--depth", "Infinity", "--json"), Object.class);
    if (projects instanceof List<?> list) {
      list.forEach(project -> collectNpm(project, listed));
    }

    final DependencyInventory inventory =
        new NodeDependencySource(CODEC)
            .read(new Project(dir, Language.JAVASCRIPT, BuildTool.PNPM, Framework.NONE));

    assertThat(inventorySet(inventory, Ecosystem.NPM)).isEqualTo(withoutMembers(listed));
  }

  @Test
  void uvMatchesUvExport(@TempDir final Path work) throws IOException {
    enabled("uv");
    final Path dir = copy("uv-single", work);
    final Set<String> listed = new TreeSet<>();
    for (final String line :
        run(
                dir,
                "uv",
                "export",
                "--no-hashes",
                "--all-packages",
                "--all-groups",
                "--all-extras",
                "--frozen")
            .lines()
            .toList()) {
      final Matcher pin = PYTHON_PIN.matcher(line);
      if (pin.find()) {
        listed.add(
            pin.group(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[-_.]+", "-")
                + "@"
                + pin.group(2));
      }
    }

    final DependencyInventory inventory =
        new PythonDependencySource()
            .read(new Project(dir, Language.PYTHON, BuildTool.UV, Framework.NONE));

    assertThat(inventorySet(inventory, Ecosystem.PYPI)).isEqualTo(listed);
  }

  @Test
  void mavenMatchesDependencyList(@TempDir final Path work) throws IOException {
    enabled("maven");
    final Path dir = copy("maven-reactor", work);
    final Set<String> listed = new TreeSet<>();
    for (final String line :
        run(dir, "mvn", "--batch-mode", "compile", "dependency:list").lines().toList()) {
      final Matcher match = MAVEN_LIST.matcher(line);
      if (match.find() && !match.group(1).equals("dev.example")) {
        listed.add(match.group(1) + ":" + match.group(2) + "@" + match.group(VERSION));
      }
    }

    final DependencyInventory inventory =
        new MavenDependencySource(PROCESSES, new FileGraphCache(), CODEC, false)
            .read(new Project(dir, Language.JAVA, BuildTool.MAVEN, Framework.NONE));

    assertThat(inventorySet(inventory, Ecosystem.MAVEN)).isEqualTo(listed);
  }

  @Test
  void gradleMatchesGradleDependencies(@TempDir final Path work) throws IOException {
    enabled("gradle");
    final Path dir = copy("gradle-multi", work);
    final Set<String> listed = new TreeSet<>();
    for (final String project : List.of(":app", ":lib")) {
      for (final String configuration : List.of("runtimeClasspath", "compileClasspath")) {
        run(
                dir,
                "gradle",
                "--console=plain",
                project + ":dependencies",
                "--configuration",
                configuration)
            .lines()
            .forEach(line -> gradleLine(line, listed));
      }
    }

    final DependencyInventory inventory =
        new GradleDependencySource(PROCESSES, new FileGraphCache(), CODEC, false)
            .read(new Project(dir, Language.JAVA, BuildTool.GRADLE, Framework.NONE));
    final Set<String> actual = inventorySet(inventory, Ecosystem.MAVEN);
    actual.removeIf(
        coordinate ->
            coordinate.contains("junit")
                || coordinate.contains("opentest4j")
                || coordinate.contains("apiguardian")
                || coordinate.contains("mapstruct"));

    assertThat(actual).isEqualTo(listed);
  }

  @Test
  void swiftMatchesShowDependencies(@TempDir final Path work) throws IOException {
    enabled("swift");
    final Path dir = copy("swiftpm", work);
    Files.writeString(
        dir.resolve("Package.swift"),
        Files.readString(dir.resolve("Package.swift"))
            .replace("        .package(path: \"../Local\"),\n", ""));
    run(dir, "swift", "package", "resolve");
    final Set<String> listed = new TreeSet<>();
    final Object tree =
        CODEC.fromJson(
            run(dir, "swift", "package", "show-dependencies", "--format", "json"), Object.class);
    collectSwift(tree, listed, true);

    final DependencyInventory inventory =
        new SwiftDependencySource(CODEC)
            .read(new Project(dir, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE));

    assertThat(inventorySet(inventory, Ecosystem.SWIFT_URL)).isEqualTo(listed);
  }

  private static void gradleLine(final String line, final Set<String> listed) {
    final Matcher match = GRADLE_LINE.matcher(line.replaceAll("^[| ]*", ""));
    if (match.find()) {
      listed.add(
          match.group(1)
              + ":"
              + match.group(2)
              + "@"
              + (match.group(MEDIATED) != null ? match.group(MEDIATED) : match.group(VERSION)));
    }
  }

  private static void collectNpm(final Object node, final Set<String> listed) {
    if (!(node instanceof Map<?, ?> map)) {
      return;
    }
    for (final String section :
        List.of("dependencies", "devDependencies", "optionalDependencies")) {
      if (map.get(section) instanceof Map<?, ?> dependencies) {
        dependencies.forEach((name, child) -> collectNpmEntry(name, child, listed));
      }
    }
  }

  private static void collectNpmEntry(
      final Object name, final Object child, final Set<String> listed) {
    if (child instanceof Map<?, ?> info) {
      final String version = info.get("version") instanceof String text ? text : "";
      if (String.valueOf(info.get("resolved")).startsWith("file:") || version.startsWith("link:")) {
        listed.add(LOCAL + name);
      } else if (!version.isEmpty()) {
        listed.add(name + "@" + version);
      }
      collectNpm(child, listed);
    }
  }

  private static void collectSwift(
      final Object node, final Set<String> listed, final boolean root) {
    if (!(node instanceof Map<?, ?> map)) {
      return;
    }
    if (!root
        && map.get("url") instanceof String url
        && map.get("version") instanceof String version) {
      final PackageId id = new PackageId(Ecosystem.SWIFT_URL, normalize(url), version);
      listed.add(id.name() + "@" + id.version());
    }
    if (map.get("dependencies") instanceof List<?> children) {
      children.forEach(child -> collectSwift(child, listed, false));
    }
  }

  private static String normalize(final String url) {
    return url.replaceFirst("^https?://", "")
        .replaceFirst("\\.git$", "")
        .toLowerCase(java.util.Locale.ROOT);
  }
}
