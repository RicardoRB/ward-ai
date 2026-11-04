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
import dev.ward.infrastructure.analysis.swift.SwiftDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The engine's Swift layer verdict equals the Harmonize rules' on the swift-architecture fixtures:
 * the clean layout passes, and each seeded forbidden type reference fails in the file that holds
 * it.
 */
class SwiftArchitectureParityTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/swift-architecture");
  private static final String MANIFEST =
      "// swift-tools-version: 5.9\n"
          + "import PackageDescription\n"
          + "\n"
          + "let package = Package(\n"
          + "    name: \"App\",\n"
          + "    targets: [.target(name: \"App\", path: \"App\")]\n"
          + ")\n";

  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(strings = {"layered", "hexagonal", "clean"})
  void theCleanLayoutPassesEveryLayerBoundary(final String style) throws IOException {
    copyFixture(style);

    assertThat(findings(ArchitectureLayers.forStyle(style, Framework.NONE))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "layered, Controller, Repository",
    "hexagonal, Domain, Infrastructure",
    "clean, Domain, Infrastructure"
  })
  void aSeededForbiddenReferenceFailsInItsFile(
      final String style, final String source, final String forbidden) throws IOException {
    copyFixture(style);
    write("App/" + forbidden + "/ViolationRecord.swift", "struct ViolationRecord {}\n");
    write(
        "App/" + source + "/Violation.swift",
        "struct Violation {\n    let record: ViolationRecord\n}\n");

    final Set<String> flagged =
        findings(ArchitectureLayers.forStyle(style, Framework.NONE)).stream()
            .map(Finding::file)
            .collect(Collectors.toSet());

    assertThat(flagged).as(style).containsExactly("App/" + source + "/Violation.swift");
  }

  private List<Finding> findings(final LayerBoundary boundary) {
    return LayerPorts.findings(
        new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE),
        boundary,
        new ProjectDependencyAdapters(List.of(new SwiftDependencyAdapters())));
  }

  private void copyFixture(final String style) throws IOException {
    final Path source = FIXTURES.resolve(style);
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        write(source.relativize(file).toString(), Files.readString(file));
      }
    }
    write("Package.swift", MANIFEST);
  }

  private void write(final String relative, final String content) throws IOException {
    final Path target = root.resolve(relative);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }
}
