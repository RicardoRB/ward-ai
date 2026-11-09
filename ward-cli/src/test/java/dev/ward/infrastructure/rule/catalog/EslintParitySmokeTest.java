package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
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
 * Runs the real ESLint named by {@code -Dward.eslint.binary} on the fixtures of every implemented
 * TypeScript and JavaScript pair whose ESLint rule has the row's name, and checks that ESLint
 * reports the same lines as the fixture's expected positions. Columns are not compared: where Ward
 * reports another column on the same line, the difference is listed in the pull request. The test
 * is skipped when the property is not set.
 */
class EslintParitySmokeTest {

  private static final String BINARY_PROPERTY = "ward.eslint.binary";
  private static final String PARSER = "@typescript-eslint/parser/dist/index.js";
  private static final String FORMATTER = "formatter.mjs";
  private static final String CONFIG = "eslint.config.mjs";
  private static final String FORMATTER_SOURCE =
      """
      export default function (results) {
        const lines = [];
        for (const result of results) {
          for (const message of result.messages) {
            lines.push([result.filePath, message.line, message.column, message.ruleId].join("\\t"));
          }
        }
        return lines.join("\\n");
      }
      """;
  private static final String SEPARATOR = "\t";
  private static final int FIELDS = 4;
  private static final int FILE = 0;
  private static final int LINE = 1;
  private static final int RULE = 3;

  /** The rows that ESLint cannot parse for their fixtures, which the test skips. */
  private static final Set<String> APPROXIMATED = EslintParityApproximations.parseErrorRows();

  /** The fields of an approximation line: the id, the language and the reason. */
  /** Octal escapes are only legal in sloppy mode, so these rows lint their fixtures as scripts. */
  private static final Set<String> SCRIPT_RULES =
      Set.of(
          "no-delete-var",
          "no-dupe-args",
          "no-octal",
          "no-octal-escape",
          "no-nonoctal-decimal-escape",
          "no-with");

  /** Globals that the rules read as environment names; the parity fixtures use them as globals. */
  private static final String GLOBALS = "{ setTimeout: \"readonly\", setInterval: \"readonly\" }";

  /** The rule of a copied case and the option table from its options file. */
  private record CaseOptions(String rule, Map<String, Object> values) {}

  /** One implemented pair with its catalog row and language. */
  private record Pair(CatalogRow row, AnalysisLanguage language) {

    String folder() {
      return language == AnalysisLanguage.TYPESCRIPT ? "typescript" : "javascript";
    }

    String rule() {
      return row.name();
    }
  }

  @Test
  void eslintReportsTheSameLinesAsTheFixtures() throws IOException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    Assumptions.assumeTrue(binary != null, "set -D" + BINARY_PROPERTY + " to run ESLint");
    final Path eslint = Path.of(binary).toRealPath();
    final Path workspace = Files.createTempDirectory("ward-eslint-parity");
    final List<Pair> pairs = implementedPairs();
    final Map<String, CaseOptions> caseOptions = new HashMap<>();
    final Map<String, List<String>> expected = copyFixtures(workspace, pairs, caseOptions);
    writeConfig(workspace, eslint, pairs, caseOptions);
    final List<String> output = run(eslint, workspace);
    assertThat(compare(pairs, expected, output)).isEmpty();
  }

  private static List<Pair> implementedPairs() {
    final List<Pair> pairs = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (row.counterparts().getOrDefault("eslint", List.of()).contains(row.name())) {
        addPairs(pairs, row);
      }
    }
    return pairs;
  }

  private static void addPairs(final List<Pair> pairs, final CatalogRow row) {
    for (final AnalysisLanguage language : row.languages(CatalogStatus.IMPLEMENTED)) {
      if (language == AnalysisLanguage.TYPESCRIPT || language == AnalysisLanguage.JAVASCRIPT) {
        final String folder = language == AnalysisLanguage.TYPESCRIPT ? "typescript" : "javascript";
        if (!APPROXIMATED.contains("ward:" + folder + "/" + row.name())) {
          pairs.add(new Pair(row, language));
        }
      }
    }
  }

  /**
   * Copies each violating and passing fixture; returns the expected lines of each copy. A case's
   * options are recorded per copied violating file, so its config applies them.
   */
  private static Map<String, List<String>> copyFixtures(
      final Path workspace, final List<Pair> pairs, final Map<String, CaseOptions> caseOptions)
      throws IOException {
    final Map<String, List<String>> expected = new HashMap<>();
    for (final Pair pair : pairs) {
      final RuleFixtureSet set =
          RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language());
      for (final String name : set.cases()) {
        final String violating = copy(workspace, pair, set.violating(name));
        expected.put(
            violating,
            set.expected(name).stream()
                .map(position -> position.substring(0, position.indexOf(':')))
                .toList());
        recordOptions(caseOptions, pair, violating, set.options(name));
      }
      // The passing fixture is shared by every case of the rule.
      final String passing = copy(workspace, pair, set.passing(""));
      expected.put(passing, List.of());
    }
    return expected;
  }

  private static String copy(final Path workspace, final Pair pair, final Path source)
      throws IOException {
    final String relative =
        pair.folder() + "/" + pair.rule() + "/" + source.getFileName().toString();
    final Path target = workspace.resolve(relative);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return relative;
  }

  /** Records a case's options against its copied violating file, for the config to apply. */
  private static void recordOptions(
      final Map<String, CaseOptions> caseOptions,
      final Pair pair,
      final String violating,
      final Map<String, Object> options) {
    if (!options.isEmpty()) {
      caseOptions.put(violating, new CaseOptions(pair.rule(), options));
    }
  }

  private static void writeConfig(
      final Path workspace,
      final Path eslint,
      final List<Pair> pairs,
      final Map<String, CaseOptions> caseOptions)
      throws IOException {
    final Path parser = eslint.getParent().getParent().getParent().resolve(PARSER);
    final String rules = rulesObject(pairs);
    final List<String> lines = new ArrayList<>();
    lines.add("import tsParser from \"%s\";".formatted(parser.toUri()));
    lines.add("export default [");
    lines.add(
        "  { files: [\"**/*.ts\"], languageOptions: { parser: tsParser, sourceType: \"module\", globals: %s }, rules: %s },"
            .formatted(GLOBALS, rules));
    lines.add(
        "  { files: [\"**/*.js\"], languageOptions: { sourceType: \"module\", globals: %s }, rules: %s },"
            .formatted(GLOBALS, rules));
    for (final Pair pair : pairs) {
      if (SCRIPT_RULES.contains(pair.rule())) {
        lines.add(
            "  { files: [\"%s/%s/**\"], languageOptions: { sourceType: \"script\" } },"
                .formatted(pair.folder(), pair.rule()));
      }
    }
    for (final Map.Entry<String, CaseOptions> entry : caseOptions.entrySet()) {
      lines.add(
          "  { files: [\"%s\"], rules: { \"%s\": [\"error\", %s] } },"
              .formatted(
                  entry.getKey(),
                  entry.getValue().rule(),
                  EslintParityOptions.jsObject(entry.getValue().values())));
    }
    lines.add("];");
    Files.writeString(workspace.resolve(CONFIG), String.join("\n", lines) + "\n");
    Files.writeString(workspace.resolve(FORMATTER), FORMATTER_SOURCE);
  }

  private static String rulesObject(final List<Pair> pairs) {
    final Set<String> rules = new TreeSet<>();
    for (final Pair pair : pairs) {
      rules.add(pair.rule());
    }
    final List<String> entries = new ArrayList<>();
    for (final String rule : rules) {
      entries.add("\"" + rule + "\": \"error\"");
    }
    return "{ " + String.join(", ", entries) + " }";
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
    final String root = workspace.toRealPath() + "/";
    return stdout.lines().map(line -> line.replace(root, "")).toList();
  }

  /** Compares the lines per file and rule; each file's findings must match its expected lines. */
  private static List<String> compare(
      final List<Pair> pairs, final Map<String, List<String>> expected, final List<String> output) {
    final Map<String, Set<String>> found =
        output.stream()
            .map(line -> line.split(SEPARATOR))
            .filter(fields -> fields.length == FIELDS && !"null".equals(fields[RULE]))
            .collect(
                Collectors.groupingBy(
                    fields -> fields[FILE] + "#" + fields[RULE],
                    TreeMap::new,
                    Collectors.mapping(
                        fields -> fields[LINE], Collectors.toCollection(TreeSet::new))));
    final Map<String, Set<String>> wanted =
        expected.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> new TreeSet<>(entry.getValue())));
    final List<String> problems = new ArrayList<>();
    for (final Pair pair : pairs) {
      final String prefix = pair.folder() + "/" + pair.rule() + "/";
      for (final String file : wanted.keySet()) {
        if (file.startsWith(prefix)) {
          final Set<String> actual = found.getOrDefault(file + "#" + pair.rule(), Set.of());
          if (!actual.equals(wanted.get(file))) {
            problems.add(
                file + " ESLint lines " + actual + " but fixture expects " + wanted.get(file));
          }
        }
      }
    }
    return problems;
  }
}
