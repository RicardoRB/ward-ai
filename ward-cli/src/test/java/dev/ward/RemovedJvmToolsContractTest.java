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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the removal of Checkstyle, PMD, SpotBugs, Error Prone, NullAway and Semgrep {@code p/java}
 * (#347 part A): Ward no longer wires any of them as a gate, finding source, plugin, Gradle entry,
 * preset rule file, or init default, and no doc page or skill tells users to use them. Ward's own
 * rules replace them. The rule catalog, the rule provenance comments and the rule engine's class
 * names are out of scope: they describe upstream rules, not wiring. The tests run from the ward-cli
 * module, as the other source-tree contract tests do.
 */
class RemovedJvmToolsContractTest {
  /** Lower-case names of the removed tools and the Semgrep pack that only they used. */
  private static final List<String> REMOVED_TOOLS =
      List.of(
          "checkstyle", "pmd", "spotbugs", "errorprone", "error-prone", "error prone", "nullaway");

  /** The removed Java Semgrep pack; {@code p/javascript} is a different pack. */
  private static final Pattern SEMGREP_JAVA_PACK = Pattern.compile("p/java(?!script)");

  /** Biome's reporter flag for checkstyle-format XML: the format's name, not a tool. */
  private static final String BIOME_CHECKSTYLE_REPORTER = "--reporter=checkstyle";

  /** The repository root, one level above the ward-cli module. */
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  /** Starts of comment lines, where rule provenance may name a removed tool. */
  private static final List<String> COMMENT_STARTS = List.of("//", "/*", "*", "#", "<!--");

  /** A line of code: not a comment and not the Biome checkstyle reporter argument. */
  private static final Predicate<String> CODE_LINE =
      line -> {
        final String code = line.trim();
        return COMMENT_STARTS.stream().noneMatch(code::startsWith)
            && !code.contains(BIOME_CHECKSTYLE_REPORTER);
      };

  /** Generated reference pages of the rule catalog, which name the upstream tools they cite. */
  private static final String GENERATED_RULE_REFERENCE = "/reference/rules";

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
  void wardCliMainJavaNamesRemovedToolsOnlyInComments() throws IOException {
    assertThat(violations(sourceFiles("ward-cli", "java"), CODE_LINE))
        .as("code that names a removed tool")
        .isEmpty();
  }

  @Test
  void wardCliMainResourcesNameRemovedToolsOnlyInComments() throws IOException {
    assertThat(violations(sourceFiles("ward-cli", "resources"), CODE_LINE))
        .as("resource lines that name a removed tool")
        .isEmpty();
  }

  @Test
  void skillsAndUserDocsNameNoRemovedTool() throws IOException {
    assertThat(
            violations(
                docFiles(),
                line -> !(line.contains(BIOME_CHECKSTYLE_REPORTER) || line.contains("ward:"))))
        .as("doc lines that name a removed tool")
        .isEmpty();
  }

  private List<String> violations(final List<Path> files, final Predicate<String> rule)
      throws IOException {
    final List<String> violations = new ArrayList<>();
    for (final Path file : files) {
      for (final String line : namingLines(file)) {
        if (rule.test(line)) {
          violations.add(relative(file) + ": " + line.trim());
        }
      }
    }
    return violations;
  }

  private static boolean namesRemovedTool(final String text) {
    final String lower = text.toLowerCase(Locale.ROOT);
    return REMOVED_TOOLS.stream().anyMatch(lower::contains)
        || SEMGREP_JAVA_PACK.matcher(lower).find();
  }

  /** Lines of a text file that name a removed tool, in any case. */
  private static List<String> namingLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(RemovedJvmToolsContractTest::namesRemovedTool)
        .toList();
  }

  /** Files under {@code <module>/src/main/<subtree>}, in a stable order. */
  private static List<Path> sourceFiles(final String module, final String subtree)
      throws IOException {
    final Path tree = ROOT.resolve(module).resolve("src/main").resolve(subtree);
    if (!Files.isDirectory(tree)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(tree)) {
      return paths.filter(Files::isRegularFile).sorted().toList();
    }
  }

  /** Skill and user documentation files, excluding the generated rule reference. */
  private static List<Path> docFiles() throws IOException {
    final List<Path> files = new ArrayList<>();
    files.add(ROOT.resolve("README.md"));
    for (final String tree : List.of("skills", "website/src/content/docs")) {
      final Path root = ROOT.resolve(tree);
      if (Files.isDirectory(root)) {
        try (Stream<Path> paths = Files.walk(root)) {
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().matches(".*\\.(md|mdx)$"))
              .filter(path -> !relative(path).contains(GENERATED_RULE_REFERENCE))
              .sorted()
              .forEach(files::add);
        }
      }
    }
    return files;
  }

  private static String relative(final Path file) {
    return ROOT.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }
}
