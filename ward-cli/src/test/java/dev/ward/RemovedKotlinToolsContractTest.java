package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the removal of detekt and ktlint (#450): Ward never wired either tool, so neither may come
 * back as a gate, a finding source, a plugin, a Gradle entry or a main class. Ward's Kotlin rules
 * replace them. The tests run from the ward-cli module, as the other source-tree contract tests do.
 */
class RemovedKotlinToolsContractTest {
  /** Lower-case names of the removed tools. */
  private static final List<String> REMOVED_TOOLS = List.of("detekt", "ktlint");

  /** The repository root, one level above the ward-cli module. */
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  /**
   * Main Java files that may name a removed tool, with how many lines may name it. A new mention
   * changes the count and fails the test, so the allowlist cannot hide one.
   */
  private static final Map<String, Integer> ALLOWED_JAVA_LINES =
      Map.of(
          // #449 removes this comment; drop the entry when it merges.
          "ward-cli/src/main/java/dev/ward/application/init/jvm/JvmInitSupport.java",
          1,
          // Provenance: Ward's style is not ktlint's.
          "ward-frontend-antlr/src/main/java/dev/ward/analysis/frontend/antlr/format/kotlin/"
              + "KotlinFormatter.java",
          1,
          // Provenance: the potential-bugs set of detekt.
          "ward-lang-jvm/src/main/java/dev/ward/analysis/application/jvm/KotlinBugChecks.java",
          1);

  /** Directory of the Kotlin rule files, whose provenance comments may name a removed tool. */
  private static final String KOTLIN_RULES = "/rules/kotlin/";

  @Test
  void qualityGateDefinitionsDoNotNameRemovedTools() {
    assertThat(QualityGateDefinitions.all())
        .allSatisfy(
            definition ->
                assertThat(namesRemovedTool(definition.id() + " " + definition.displayName()))
                    .as("gate %s", definition.id())
                    .isFalse());
  }

  @Test
  void mainJavaSourcesNameRemovedToolsOnlyInTheAllowlist() throws IOException {
    final Map<String, Integer> named = new TreeMap<>();
    for (final Path file : sourceFiles("java")) {
      final long lines = namingLines(file).size();
      if (lines > 0) {
        named.put(relative(file), (int) lines);
      }
    }
    assertThat(named).isEqualTo(new TreeMap<>(ALLOWED_JAVA_LINES));
  }

  @Test
  void mainResourcesNameRemovedToolsOnlyInKotlinRuleComments() throws IOException {
    final List<String> violations = new ArrayList<>();
    for (final Path file : sourceFiles("resources")) {
      final String path = relative(file);
      for (final String line : namingLines(file)) {
        if (!path.contains(KOTLIN_RULES) || !line.trim().startsWith("#")) {
          violations.add(path + ": " + line.trim());
        }
      }
    }
    assertThat(violations).isEmpty();
  }

  private static boolean namesRemovedTool(final String text) {
    final String lower = text.toLowerCase(Locale.ROOT);
    return REMOVED_TOOLS.stream().anyMatch(lower::contains);
  }

  /** Lines of a text file that name a removed tool, in any case. */
  private static List<String> namingLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(RemovedKotlinToolsContractTest::namesRemovedTool)
        .toList();
  }

  /** Files under {@code src/main/<subtree>} of every module, in a stable order. */
  private static List<Path> sourceFiles(final String subtree) throws IOException {
    final List<Path> files = new ArrayList<>();
    try (Stream<Path> modules = Files.list(ROOT)) {
      for (final Path module : modules.sorted().toList()) {
        final Path tree = module.resolve("src/main").resolve(subtree);
        if (Files.isDirectory(tree)) {
          try (Stream<Path> paths = Files.walk(tree)) {
            paths.filter(Files::isRegularFile).sorted().forEach(files::add);
          }
        }
      }
    }
    return files;
  }

  private static String relative(final Path file) {
    return ROOT.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }
}
