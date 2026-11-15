package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the removal of the {@code sonar} gate type: Ward no longer wires, installs or detects the
 * Sonar scanner. A project that wants it runs it as a plain command check. The docs still name
 * SonarQube, to say what Ward does not replace, and the secrets rules still name its token. The
 * tests run from the ward-cli module, as the other source-tree contract tests do.
 */
class RemovedSonarGateContractTest {
  private static final Pattern SONAR =
      Pattern.compile("\\bsonar\\b|sonarqube|sonar-maven", Pattern.CASE_INSENSITIVE);

  /** The repository root, one level above the ward-cli module. */
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  @Test
  void noGateDefinitionIsNamedSonar() {
    assertThat(QualityGateDefinitions.all())
        .allSatisfy(
            definition ->
                assertThat(definition.id() + " " + definition.displayName())
                    .doesNotContainPattern(SONAR));
  }

  @Test
  void productionSourcesAndResourcesDoNotNameSonar() throws IOException {
    final List<Path> files = new ArrayList<>();
    try (Stream<Path> modules = Files.list(ROOT)) {
      for (final Path module : modules.sorted().toList()) {
        files.addAll(
            regularFiles(module.resolve("src/main"), ".*").stream()
                .filter(file -> !file.toString().replace('\\', '/').contains("/rules/secrets/"))
                .toList());
      }
    }
    assertThat(violations(files)).as("production lines that name Sonar").isEmpty();
  }

  private static List<String> violations(final List<Path> files) throws IOException {
    final List<String> found = new ArrayList<>();
    for (final Path file : files) {
      for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (SONAR.matcher(line).find()) {
          found.add(ROOT.relativize(file) + ": " + line.trim());
        }
      }
    }
    return found;
  }

  private static List<Path> regularFiles(final Path tree, final String namePattern)
      throws IOException {
    if (!Files.isDirectory(tree)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(tree)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().matches(namePattern))
          .sorted()
          .toList();
    }
  }
}
