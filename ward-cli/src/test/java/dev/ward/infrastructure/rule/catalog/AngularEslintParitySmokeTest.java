package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Runs the real angular-eslint named by {@code -Dward.angular-eslint.binary} on the fixtures of
 * every implemented TypeScript pair of the angular-eslint rows, and checks that it reports the same
 * lines as the fixtures' expected positions. The binary is an ESLint executable in a project whose
 * {@code node_modules} hold {@code @angular-eslint/eslint-plugin} and
 * {@code @typescript-eslint/parser} (for example {@code node_modules/eslint/bin/eslint.js}). Known
 * differences are listed in {@code parity/angular-eslint-approximations.txt}, and a rule with one
 * is not compared. The test is skipped when the property is not set.
 */
class AngularEslintParitySmokeTest {

  private static final String BINARY_PROPERTY = "ward.angular-eslint.binary";
  private static final String COUNTERPART = "angular-eslint";
  private static final String PARSER = "@typescript-eslint/parser/dist/index.js";
  private static final String PLUGIN = "@angular-eslint/eslint-plugin/dist/index.js";
  private static final String APPROXIMATIONS = "/parity/angular-eslint-approximations.txt";
  private static final String CONFIG = "eslint.config.mjs";
  private static final String FORMATTER = "formatter.mjs";
  private static final String FORMATTER_SOURCE =
      """
      export default function (results) {
        const lines = [];
        for (const result of results) {
          for (const message of result.messages) {
            lines.push([result.filePath, message.line, message.ruleId].join("\\t"));
          }
        }
        return lines.join("\\n");
      }
      """;
  private static final String SEPARATOR = "\t";
  private static final int FIELDS = 3;
  private static final int FILE = 0;
  private static final int LINE = 1;
  private static final int RULE = 2;

  @Test
  void angularEslintReportsTheSameLinesAsTheFixtures() throws IOException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    Assumptions.assumeTrue(binary != null, "set -D" + BINARY_PROPERTY + " to run angular-eslint");
    final Path eslint = Path.of(binary).toRealPath();
    final Path modules = eslint.getParent().getParent().getParent();
    final Path workspace = Files.createTempDirectory("ward-angular-eslint-parity");
    final List<CatalogRow> rows = implementedRows();
    final Map<String, List<String>> expected = copyFixtures(workspace, rows);
    writeConfig(workspace, modules, rows);
    final List<String> output = run(eslint, workspace);
    assertThat(compare(rows, expected, output)).isEmpty();
  }

  /** The names of the implemented TypeScript rows that angular-eslint names too. */
  private static List<CatalogRow> implementedRows() throws IOException {
    final List<CatalogRow> rows = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (row.counterparts().getOrDefault(COUNTERPART, List.of()).contains(row.name())
          && row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.TYPESCRIPT)) {
        rows.add(row);
      }
    }
    return rows;
  }

  /** Copies each violating and passing fixture; returns the expected lines of each copy. */
  private static Map<String, List<String>> copyFixtures(
      final Path workspace, final List<CatalogRow> rows) throws IOException {
    final Map<String, List<String>> expected = new HashMap<>();
    for (final CatalogRow row : rows) {
      final RuleFixtureSet set =
          RuleFixtureSet.of(RuleFixtureSet.root(), row, AnalysisLanguage.TYPESCRIPT);
      for (final String name : set.cases()) {
        final String violating = copy(workspace, row.name(), "violating.ts", set.violating(name));
        expected.put(violating, lines(set.expected(name)));
        expected.put(copy(workspace, row.name(), "passing.ts", set.passing(name)), List.of());
      }
    }
    return expected;
  }

  private static String copy(
      final Path workspace, final String row, final String file, final Path source)
      throws IOException {
    final String relative = row + "/" + file;
    final Path target = workspace.resolve(relative);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return relative;
  }

  private static List<String> lines(final List<String> expected) {
    final List<String> lines = new ArrayList<>();
    for (final String position : expected) {
      lines.add(position.substring(0, position.indexOf(':')));
    }
    return lines;
  }

  private static void writeConfig(
      final Path workspace, final Path modules, final List<CatalogRow> rows) throws IOException {
    final Map<String, String> entries = new TreeMap<>();
    for (final CatalogRow row : rows) {
      entries.put("\"@angular-eslint/" + row.name() + "\"", "\"error\"");
    }
    final String rules =
        "{ "
            + entries.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "))
            + " }";
    final String config =
        "import tsParser from \"%s\";\nimport angular from \"%s\";\n"
                .formatted(modules.resolve(PARSER).toUri(), modules.resolve(PLUGIN).toUri())
            + "export default [\n"
            + "  { files: [\"**/*.ts\"], languageOptions: { parser: tsParser, sourceType: \"script\" },"
            + " plugins: { \"@angular-eslint\": angular }, rules: %s },\n".formatted(rules)
            + "];\n";
    Files.writeString(workspace.resolve(CONFIG), config);
    Files.writeString(workspace.resolve(FORMATTER), FORMATTER_SOURCE);
  }

  private static List<String> run(final Path eslint, final Path workspace)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(eslint.toString(), "-c", CONFIG, "-f", "./" + FORMATTER, ".")
            .directory(workspace.toFile())
            .start();
    final String stdout =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final String prefix = workspace.toRealPath() + "/";
    return stdout.lines().map(line -> line.replace(prefix, "")).toList();
  }

  /** Compares the lines per file; a rule with a listed approximation is not compared. */
  private static List<String> compare(
      final List<CatalogRow> rows,
      final Map<String, List<String>> expected,
      final List<String> output)
      throws IOException {
    final Map<String, Set<String>> found =
        output.stream()
            .map(line -> line.split(SEPARATOR))
            .filter(fields -> fields.length == FIELDS)
            .collect(
                Collectors.groupingBy(
                    fields -> fields[FILE] + "#" + fields[RULE],
                    TreeMap::new,
                    Collectors.mapping(
                        fields -> fields[LINE], Collectors.toCollection(TreeSet::new))));
    final Map<String, Set<String>> wantedLines =
        expected.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> new TreeSet<>(entry.getValue())));
    final Set<String> approximated = approximatedRules();
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : rows) {
      if (approximated.contains(row.name())) {
        continue;
      }
      for (final Map.Entry<String, List<String>> fixture : expected.entrySet()) {
        if (fixture.getKey().startsWith(row.name() + "/")) {
          final Set<String> actual =
              found.getOrDefault(fixture.getKey() + "#@angular-eslint/" + row.name(), Set.of());
          final Set<String> wanted = wantedLines.get(fixture.getKey());
          if (!actual.equals(wanted)) {
            problems.add(
                fixture.getKey()
                    + " angular-eslint lines "
                    + actual
                    + " but fixture expects "
                    + wanted);
          }
        }
      }
    }
    return problems;
  }

  private static Set<String> approximatedRules() throws IOException {
    try (InputStream stream =
        AngularEslintParitySmokeTest.class.getResourceAsStream(APPROXIMATIONS)) {
      assertThat(stream).as("approximations of %s", APPROXIMATIONS).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.startsWith("#") && !line.isBlank())
          .map(line -> line.split(SEPARATOR)[0])
          .collect(Collectors.toSet());
    }
  }
}
