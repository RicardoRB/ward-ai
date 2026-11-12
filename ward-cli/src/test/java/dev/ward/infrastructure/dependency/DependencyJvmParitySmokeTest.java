package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.gradle.GradleDependencySource;
import dev.ward.infrastructure.dependency.maven.MavenDependencySource;
import dev.ward.infrastructure.dependency.swift.SwiftDependencySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compares the Maven, Gradle and Swift inventories with the tools. */
class DependencyJvmParitySmokeTest {
  private static final int VERSION = 3;
  private static final int MEDIATED = 4;
  private static final Pattern MAVEN_LIST =
      Pattern.compile(
          "^\\[INFO]\\s+([\\w.\\-]+):([\\w.\\-]+):[\\w\\-]+:(?:[\\w\\-]+:)?([^:\\s]+):\\w+");
  private static final Pattern GRADLE_LINE =
      Pattern.compile(
          "[+\\\\]--- ([\\w.\\-]+):([\\w.\\-]+):([^\\s]+?)(?: -> ([^\\s]+))?(?: \\(.\\))?$");

  @Test
  void mavenMatchesDependencyList(@TempDir final Path work) throws IOException {
    ParitySupport.enabled("maven");
    final Path dir = ParitySupport.copy("maven-reactor", work);
    final Set<String> listed = new TreeSet<>();
    for (final String line :
        ParitySupport.run(dir, "mvn", "--batch-mode", "compile", "dependency:list")
            .lines()
            .toList()) {
      final Matcher match = MAVEN_LIST.matcher(line);
      if (match.find() && !"dev.example".equals(match.group(1))) {
        listed.add(match.group(1) + ":" + match.group(2) + "@" + match.group(VERSION));
      }
    }

    final DependencyInventory inventory =
        new MavenDependencySource(
                ParitySupport.PROCESSES, new FileGraphCache(), ParitySupport.CODEC, false)
            .read(new Project(dir, Language.JAVA, BuildTool.MAVEN, Framework.NONE));

    assertThat(ParitySupport.inventorySet(inventory, Ecosystem.MAVEN)).isEqualTo(listed);
  }

  @Test
  void gradleMatchesGradleDependencies(@TempDir final Path work) throws IOException {
    ParitySupport.enabled("gradle");
    final Path dir = ParitySupport.copy("gradle-multi", work);
    final Set<String> listed = new TreeSet<>();
    for (final String project : List.of(":app", ":lib")) {
      for (final String configuration : List.of("runtimeClasspath", "compileClasspath")) {
        ParitySupport.run(
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
        new GradleDependencySource(
                ParitySupport.PROCESSES, new FileGraphCache(), ParitySupport.CODEC, false)
            .read(new Project(dir, Language.JAVA, BuildTool.GRADLE, Framework.NONE));
    final Set<String> actual = ParitySupport.inventorySet(inventory, Ecosystem.MAVEN);
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
    ParitySupport.enabled("swift");
    final Path dir = ParitySupport.copy("swiftpm", work);
    Files.writeString(
        dir.resolve("Package.swift"),
        Files.readString(dir.resolve("Package.swift"))
            .replace("        .package(path: \"../Local\"),\n", ""));
    ParitySupport.run(dir, "swift", "package", "resolve");
    final Set<String> listed = new TreeSet<>();
    final Object tree =
        ParitySupport.CODEC.fromJson(
            ParitySupport.run(dir, "swift", "package", "show-dependencies", "--format", "json"),
            Object.class);
    collectSwift(tree, listed, true);

    final DependencyInventory inventory =
        new SwiftDependencySource(ParitySupport.CODEC)
            .read(new Project(dir, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE));

    assertThat(ParitySupport.inventorySet(inventory, Ecosystem.SWIFT_URL)).isEqualTo(listed);
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
