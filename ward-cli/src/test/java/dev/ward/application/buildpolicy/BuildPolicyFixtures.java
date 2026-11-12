package dev.ward.application.buildpolicy;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.BuildRule;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.buildpolicy.FileSystemBuildFiles;
import dev.ward.infrastructure.buildpolicy.gradle.GradleBuildReader;
import dev.ward.infrastructure.buildpolicy.maven.MavenBuildReader;
import dev.ward.infrastructure.buildpolicy.node.NodeBuildReader;
import dev.ward.infrastructure.buildpolicy.python.PythonBuildReader;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Builds a build policy service over the real readers and runs a rule on a project folder. */
final class BuildPolicyFixtures {
  static final Path ROOT = Path.of("src/test/resources/build-policy");

  private BuildPolicyFixtures() {}

  static BuildPolicyService service() {
    final JsonCodec codec = new ForyJsonCodec();
    final List<BuildModelReader> readers =
        List.of(
            new MavenBuildReader(),
            new GradleBuildReader(),
            new NodeBuildReader(codec),
            new PythonBuildReader());
    return new BuildPolicyService(
        new FileSystemBuildFiles(readers), new DependencyInventoryService(Map.of()));
  }

  static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  /** Returns the {@code path:line} of each finding of {@code rule} in {@code root}. */
  static List<String> findings(
      final Path root, final BuildRule rule, final Map<String, String> options) {
    return service()
        .run(project(root), "policy", List.of(new BuildPolicySetting(rule, false, options)))
        .stream()
        .map(finding -> finding.file() + ":" + finding.line())
        .toList();
  }

  /** Reads {@code options.txt} of a fixture: one {@code key=value} per line. */
  static Map<String, String> options(final Path folder) throws IOException {
    final Path file = folder.resolve("options.txt");
    final Map<String, String> options = new LinkedHashMap<>();
    if (Files.isRegularFile(file)) {
      for (final String line : Files.readAllLines(file)) {
        options.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
      }
    }
    return options;
  }

  static List<Path> folders(final Path parent) throws IOException {
    try (Stream<Path> entries = Files.list(parent)) {
      return entries.filter(Files::isDirectory).sorted().toList();
    }
  }
}
