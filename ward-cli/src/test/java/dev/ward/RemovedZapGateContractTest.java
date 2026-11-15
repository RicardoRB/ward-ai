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
 * Guards the removal of the {@code zap} gate type: Ward no longer wires, installs, detects or
 * documents Zap. A project that wants it runs it as a plain command check, which the gates page of
 * the docs explains (so that page is the one file allowed to name it). The tests run from the
 * ward-cli module, as the other source-tree contract tests do.
 */
class RemovedZapGateContractTest {
  private static final Pattern ZAP = Pattern.compile("\\bzap\\b", Pattern.CASE_INSENSITIVE);

  /** The repository root, one level above the ward-cli module. */
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  @Test
  void noGateDefinitionIsNamedZap() {
    assertThat(QualityGateDefinitions.all())
        .allSatisfy(
            definition ->
                assertThat(definition.id() + " " + definition.displayName())
                    .doesNotContainPattern(ZAP));
  }

  @Test
  void productionSourcesAndResourcesDoNotNameZap() throws IOException {
    final List<Path> files = new ArrayList<>();
    try (Stream<Path> modules = Files.list(ROOT)) {
      for (final Path module : modules.sorted().toList()) {
        files.addAll(regularFiles(module.resolve("src/main"), ".*"));
      }
    }
    assertThat(violations(files)).as("production lines that name Zap").isEmpty();
  }

  @Test
  void skillsAndUserDocsDoNotNameZap() throws IOException {
    final List<Path> files = new ArrayList<>(List.of(ROOT.resolve("README.md")));
    files.addAll(regularFiles(ROOT.resolve("skills"), ".*\\.(md|mdx)"));
    files.addAll(
        regularFiles(ROOT.resolve("website/src/content/docs"), ".*\\.(md|mdx)").stream()
            .filter(file -> !file.toString().replace('\\', '/').contains("/reference/rules"))
            .filter(file -> !file.endsWith("configuration/gates.mdx"))
            .toList());
    assertThat(violations(files)).as("doc lines that name Zap").isEmpty();
  }

  private static List<String> violations(final List<Path> files) throws IOException {
    final List<String> found = new ArrayList<>();
    for (final Path file : files) {
      for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (ZAP.matcher(line).find()) {
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
