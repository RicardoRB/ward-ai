package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Runs the real ESLint named by {@code -Dward.eslint.binary} on the fixtures of every implemented
 * TypeScript and JavaScript pair, and checks that ESLint reports the same lines as the fixture's
 * expected positions. Columns are not compared: where Ward reports a different column for the same
 * line, the difference is listed in the pull request. It is skipped when the property is not set.
 */
class EslintParitySmokeTest {

  private static final String BINARY_PROPERTY = "ward.eslint.binary";
  private static final String FORMATTER = "formatter.mjs";
  private static final String FORMATTER_SOURCE =
      "export default function (results) {\n"
          + "  const lines = [];\n"
          + "  for (const result of results) {\n"
          + "    for (const message of result.messages) {\n"
          + "      lines.push([result.filePath, message.line, message.column, message.ruleId].join(\"\\t\"));\n"
          + "    }\n"
          + "  }\n"
          + "  return lines.join(\"\\n\");\n"
          + "}\n";
  private static final String PARSER_RELATIVE = "@typescript-eslint/parser/dist/index.js";

  @Test
  void eslintReportsTheSameLinesAsTheFixtures() throws IOException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    Assumptions.assumeTrue(binary != null, "set -D" + BINARY_PROPERTY + " to run ESLint");
    final Path eslint = Path.of(binary).toRealPath();
    final Path workspace = Files.createTempDirectory("ward-eslint-parity");
    final List<Pair> pairs = implementedPairs();
    final Map<String, List<String>> expected = copyFixtures(workspace, pairs);
    writeConfig(workspace, eslint, pairs);
    final List<String> output = run(eslint, workspace);
    final List<String> problems = compare(pairs, expected, output);
    assertThat(problems).isEmpty();
  }

  /** One implemented pair with its rule id and language. */
  private record Pair(CatalogRow row, AnalysisLanguage language) {
    String rule() {
      return row.name();
    }
  }

  private static List<Pair> implementedPairs() {
    final List<Pair> pairs = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.counterparts().getOrDefault("eslint", List.of()).contains(row.name())) {
        continue;
      }
      for (final AnalysisLanguage language : row.languages(CatalogStatus.IMPLEMENTED)) {
        if (language == AnalysisLanguage.TYPESCRIPT || language == AnalysisLanguage.JAVASCRIPT) {
          pairs.add(new Pair(row, language));
        }
      }
    }
    return pairs;
  }

  /** Copies each violating and passing fixture; returns the expected lines per copied file. */
  private static Map<String, List<String>> copyFixtures(
      final Path workspace, final List<Pair> pairs) throws IOException {
    final Map<String, List<String>> expected = new java.util.HashMap<>();
    for (final Pair pair : pairs) {
      final RuleFixtureSet set =
          RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language());
      final String extension = RuleFixtureSet.extension(pair.language());
      for (final String name : set.cases()) {
        final String violating = relative(pair, "violating." + extension);
        Files.createDirectories(workspace.resolve(violating).getParent());
        Files.copy(set.violating(name), workspace.resolve(violating));
        expected.put(violating, lines(set.expected(name)));
        final String passing = relative(pair, "passing." + extension);
        Files.copy(set.passing(name), workspace.resolve(passing));
        expected.put(passing, List.of());
      }
    }
    return expected;
  }

  private static List<String> lines(final List<String> expected) {
    final List<String> lines = new ArrayList<>();
    for (final String position : expected) {
      lines.add(position.substring(0, position.indexOf(':')));
    }
    return lines;
  }

  private static String relative(final Pair pair, final String file) {
    return folder(pair.language()) + "/" + pair.rule() + "/" + file;
  }

  private static String folder(final AnalysisLanguage language) {
    return language == AnalysisLanguage.TYPESCRIPT ? "typescript" : "javascript";
  }

  private static void writeConfig(final Path workspace, final Path eslint, final List<Pair> pairs)
      throws IOException {
    final Path parser = eslint.getParent().getParent().getParent().resolve(PARSER_RELATIVE);
    final Set<String> rules = new TreeSet<>();
    pairs.forEach(pair -> rules.add(pair.rule()));
    final StringBuilder config = new StringBuilder();
    config.append("import tsParser from \"").append(parser.toUri()).append("\";\n");
    config.append("export default [\n");
    config.append(
        "  { files: [\"**/*.ts\"], languageOptions: { parser: tsParser, sourceType: \"script\" }, rules: ");
    config.append(rulesObject(rules)).append(" },\n");
    config.append("  { files: [\"**/*.js\"], languageOptions: { sourceType: \"script\" }, rules: ");
    config.append(rulesObject(rules)).append(" },\n");
    config.append("];\n");
    Files.writeString(workspace.resolve("eslint.config.mjs"), config.toString());
    Files.writeString(workspace.resolve(FORMATTER), FORMATTER_SOURCE);
  }

  private static String rulesObject(final Set<String> rules) {
    final StringBuilder object = new StringBuilder("{ ");
    for (final String rule : rules) {
      object.append('"').append(rule).append("\": \"error\", ");
    }
    return object.append('}').toString();
  }

  private static List<String> run(final Path eslint, final Path workspace)
      throws IOException, InterruptedException {
    final Path root = workspace.toRealPath();
    final Process process =
        new ProcessBuilder(
                eslint.toString(), "-c", "eslint.config.mjs", "-f", "./" + FORMATTER, ".")
            .directory(workspace.toFile())
            .redirectErrorStream(false)
            .start();
    final List<String> lines =
        new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .lines()
            .toList();
    process.waitFor();
    return lines.stream().map(line -> line.replace(root + "/", "")).toList();
  }

  /** Compares the lines per file and rule; a file's findings must match its expected lines. */
  private static List<String> compare(
      final List<Pair> pairs, final Map<String, List<String>> expected, final List<String> output) {
    final Map<String, Set<String>> found = new java.util.HashMap<>();
    for (final String line : output) {
      final String[] fields = line.split("\t");
      if (fields.length == 4 && !"null".equals(fields[3])) {
        final String file = fields[0];
        found.computeIfAbsent(file + "#" + fields[3], key -> new LinkedHashSet<>()).add(fields[1]);
      }
    }
    final List<String> problems = new ArrayList<>();
    for (final Pair pair : pairs) {
      for (final String file : expected.keySet()) {
        if (!file.startsWith(folder(pair.language()) + "/" + pair.rule() + "/")) {
          continue;
        }
        final Set<String> actual = found.getOrDefault(file + "#" + pair.rule(), Set.of());
        final Set<String> wanted = new TreeSet<>(expected.get(file));
        if (!new TreeSet<>(actual).equals(wanted)) {
          problems.add(file + " ESLint lines " + actual + " but fixture expects " + wanted);
        }
      }
    }
    return problems;
  }
}
