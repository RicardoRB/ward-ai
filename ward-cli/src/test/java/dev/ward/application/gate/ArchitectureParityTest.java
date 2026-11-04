package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The engine's layer verdict on the node-architecture fixtures equals the files that
 * dependency-cruiser flags, given the same boundaries. The golden {@code depcruise-edges.json}
 * holds the project-file edges dependency-cruiser sees, so the expected files are computed from
 * them.
 */
class ArchitectureParityTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/node-architecture");
  private static final String GOLDEN = "depcruise-edges.json";
  private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(strings = {"typescript", "javascript"})
  void engineFlagsExactlyTheFilesDependencyCruiserFlags(final String dialect) throws IOException {
    final Path source = FIXTURES.resolve(dialect);
    copy(source);
    final List<List<String>> edges = golden(source.resolve(GOLDEN));

    for (final String style : List.of("layered", "hexagonal", "clean")) {
      final LayerBoundary boundary = ArchitectureLayers.forStyle(style, Framework.NONE);
      final Set<String> expected =
          edges.stream()
              .filter(edge -> inAny(edge.get(0), boundary.from()))
              .filter(edge -> inAny(edge.get(1), boundary.forbidden()))
              .map(edge -> edge.get(0))
              .collect(Collectors.toSet());
      final Set<String> actual =
          findings(boundary).stream().map(Finding::file).collect(Collectors.toSet());

      assertThat(actual).as(dialect + " " + style).isEqualTo(expected);
    }
  }

  /**
   * Each seeded violation, copied into the clean project as the generated tool's smoke test copies
   * it, is flagged in exactly the file that dependency-cruiser fails on, for the same style.
   */
  @ParameterizedTest
  @CsvSource({
    "typescript, layered, controller-repository, src/controller/leak.ts",
    "typescript, hexagonal, domain-infrastructure, src/domain/leak.ts",
    "typescript, clean, domain-infrastructure, src/domain/leak.ts",
    "javascript, layered, controller-repository, src/controller/leak.js",
    "javascript, hexagonal, domain-infrastructure, src/domain/leak.js",
    "javascript, clean, domain-infrastructure, src/domain/leak.js"
  })
  void theSeededViolationIsFlaggedInTheFileDependencyCruiserFailsOn(
      final String dialect, final String style, final String violation, final String file)
      throws IOException {
    copy(FIXTURES.resolve(dialect));
    final Path seeded = FIXTURES.resolve(dialect).resolve("violations").resolve(violation);
    copyTree(seeded, seeded.resolve("src"));

    final Set<String> actual =
        findings(ArchitectureLayers.forStyle(style, Framework.NONE)).stream()
            .map(Finding::file)
            .collect(Collectors.toSet());

    assertThat(actual).as(dialect + " " + style).containsExactly(file);
  }

  /** Whether one of the layer names is a directory of {@code path}, as the engine reads them. */
  private static boolean inAny(final String path, final List<String> layers) {
    final String[] segments = path.split("/");
    return layers.stream()
        .anyMatch(layer -> Stream.of(segments).limit(segments.length - 1L).anyMatch(layer::equals));
  }

  private List<Finding> findings(final LayerBoundary boundary) {
    return LayerPorts.findings(
        new Project(root, Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE),
        boundary,
        new ProjectDependencyAdapters(List.of(new NodeDependencyAdapters())));
  }

  /** Reads the golden list of {@code [from, to]} pairs, in file order. */
  private static List<List<String>> golden(final Path file) throws IOException {
    final Matcher matcher = QUOTED.matcher(Files.readString(file));
    final List<String> strings = new java.util.ArrayList<>();
    while (matcher.find()) {
      strings.add(matcher.group(1));
    }
    final List<List<String>> edges = new java.util.ArrayList<>();
    for (int index = 0; index + 1 < strings.size(); index += 2) {
      edges.add(List.of(strings.get(index), strings.get(index + 1)));
    }
    return edges;
  }

  /** Copies the fixture folder {@code source} into the project root. */
  private void copy(final Path source) throws IOException {
    copyTree(source, source);
  }

  /** Copies the files under {@code folder} to their paths relative to {@code base}, in the root. */
  private void copyTree(final Path base, final Path folder) throws IOException {
    try (Stream<Path> files = Files.walk(folder)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path target = root.resolve(base.relativize(file).toString());
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
}
