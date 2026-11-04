package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.swift.FileSystemSwiftToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Runs the generated Harmonize rules with real {@code swift test} for each style: the allowed
 * layout passes, and adding a dependency from the source layer into a forbidden one fails. Requires
 * macOS with Xcode 26 or newer, and the first run downloads Harmonize and builds swift-syntax. Run
 * with {@code ./mvnw -Dtest=HarmonizeRulesSmokeTest -Dward.swift.harmonize=true test}.
 */
@EnabledIfSystemProperty(named = "ward.swift.harmonize", matches = "true")
class HarmonizeRulesSmokeTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/swift-architecture");
  private static final long TIMEOUT_MINUTES = 15;
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  @TempDir Path work;

  @ParameterizedTest
  @CsvSource({
    "layered, controller, repository",
    "hexagonal, domain, infrastructure",
    "clean, domain, infrastructure"
  })
  void allowedLayoutPassesAndForbiddenDependencyFails(
      final String style, final String source, final String forbidden) throws Exception {
    copyFixture(style);
    final Project project = new Project(work, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
    new HarmonizeRulesGenerator(new FileSystemSwiftToolConfigs()).apply(project, style, NO_EXTRAS);

    final Map<String, String> allowedFiles = SwiftArchitectureFiles.fixture(style);
    final Run allowed = swiftTest();
    assertThat(allowed.exitCode()).as(allowed.output()).isZero();
    assertThat(SwiftLayerVerdicts.failingLayers(style, allowedFiles)).isEmpty();

    final Map<String, String> violatingFiles = new TreeMap<>(allowedFiles);
    violatingFiles.putAll(SwiftArchitectureFiles.violation(source, forbidden));
    write(violatingFiles);
    final Run violating = swiftTest();
    assertThat(violating.exitCode()).as(violating.output()).isNotZero();
    assertThat(violating.output()).contains("must not depend on");
    assertThat(SwiftLayerVerdicts.failingLayers(style, violatingFiles)).containsExactly(source);
  }

  private void copyFixture(final String style) throws IOException {
    final Path from = FIXTURES.resolve(style);
    try (Stream<Path> paths = Files.walk(from)) {
      for (final Path path : paths.toList()) {
        final Path target = work.resolve(from.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(target);
        } else {
          Files.copy(path, target);
        }
      }
    }
  }

  /** Writes the files of a violating layout, by path relative to the project root. */
  private void write(final Map<String, String> files) throws IOException {
    for (final Map.Entry<String, String> file : files.entrySet()) {
      final Path path = work.resolve(file.getKey());
      Files.createDirectories(path.getParent());
      Files.writeString(path, file.getValue());
    }
  }

  private Run swiftTest() throws IOException, InterruptedException {
    final Path output = work.resolve("swift-test.log");
    final Process process =
        new ProcessBuilder("swift", "test", "--package-path", ".ward/swift-architecture")
            .directory(work.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    assertThat(process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue();
    return new Run(process.exitValue(), Files.readString(output));
  }

  private record Run(int exitCode, String output) {}
}
