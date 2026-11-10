package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Runs the real ESLint named by {@code -Dward.typescript-eslint.binary} with the typescript-eslint
 * plugin on the fixtures of every implemented typescript-eslint row, and checks that it reports the
 * same lines as the fixtures' expected positions. Options are passed as each case's options file
 * sets them. Known differences are listed in {@code parity/typescript-eslint-approximations.txt}.
 * The test is skipped when the property is not set.
 */
class TypeScriptEslintParitySmokeTest {

  private static final String BINARY_PROPERTY = "ward.typescript-eslint.binary";
  private static final String PARSER = "@typescript-eslint/parser/dist/index.js";
  private static final String PLUGIN = "@typescript-eslint/eslint-plugin/dist/index.js";
  private static final String PREFIX = "@typescript-eslint/";
  private static final String TYPESCRIPT_DIR = "typescript";
  private static final String FORMATTER = "formatter.mjs";
  private static final String CONFIG = "eslint.config.mjs";
  private static final String TSCONFIG = "tsconfig.json";
  private static final String TSCONFIG_TEXT =
      "{\"compilerOptions\":{\"strict\":true,\"target\":\"es2022\",\"module\":\"esnext\","
          + "\"moduleResolution\":\"bundler\",\"noEmit\":true,\"lib\":[\"es2022\"],\"types\":[]},"
          + "\"include\":[\"**/*.ts\"]}";
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

  /** One fixture file of a row: its workspace path, expected lines and option values. */
  private record Case(
      String row, String path, List<String> expected, Map<String, Object> options, String text) {}

  @Test
  void typescriptEslintReportsTheSameLinesAsTheFixtures() throws IOException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    Assumptions.assumeTrue(binary != null, "set -D" + BINARY_PROPERTY + " to run ESLint");
    final Path eslint = Path.of(binary).toRealPath();
    final Path workspace = Files.createTempDirectory("ward-typescript-eslint-parity");
    final List<Case> cases = cases();
    for (final Case testCase : cases) {
      final Path target = workspace.resolve(testCase.path());
      Files.createDirectories(target.getParent());
      Files.writeString(target, testCase.text());
    }
    Files.writeString(workspace.resolve(TSCONFIG), TSCONFIG_TEXT);
    Files.writeString(workspace.resolve(FORMATTER), FORMATTER_SOURCE);
    Files.writeString(workspace.resolve(CONFIG), config(eslint, workspace, cases));
    final List<String> output = run(workspace);
    assertThat(compare(cases, output)).isEmpty();
  }

  /** Reads every violating and passing fixture of the implemented rows, with a module marker. */
  private static List<Case> cases() throws IOException {
    final List<Case> cases = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.counterparts().getOrDefault("typescript-eslint", List.of()).contains(row.name())
          || !row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.TYPESCRIPT)) {
        continue;
      }
      final RuleFixtureSet set =
          RuleFixtureSet.of(RuleFixtureSet.root(), row, AnalysisLanguage.TYPESCRIPT);
      for (final String name : set.cases()) {
        cases.add(caseOf(row.name(), "violating", name, set));
        if (Files.isRegularFile(set.passing(name))) {
          cases.add(caseOf(row.name(), "passing", name, set));
        }
      }
    }
    return cases;
  }

  private static Case caseOf(
      final String row, final String kind, final String name, final RuleFixtureSet set)
      throws IOException {
    final String suffix = name.isEmpty() ? "" : "-" + name;
    final Path source = "violating".equals(kind) ? set.violating(name) : set.passing(name);
    final String body = Files.readString(source, StandardCharsets.UTF_8);
    final String text = TypeScriptParityModules.text(row, body);
    final List<String> expected = "violating".equals(kind) ? lines(set.expected(name)) : List.of();
    final String extension = source.getFileName().toString().endsWith(".d.ts") ? ".d.ts" : ".ts";
    return new Case(
        row,
        TYPESCRIPT_DIR + "/" + row + "/" + kind + suffix + extension,
        expected,
        set.options(name),
        text);
  }

  private static List<String> lines(final List<String> expected) {
    return expected.stream().map(position -> position.substring(0, position.indexOf(':'))).toList();
  }

  private static String config(final Path eslint, final Path workspace, final List<Case> cases) {
    final Path modules = eslint.getParent().getParent().getParent();
    final Path parser = modules.resolve(PARSER);
    final Path plugin = modules.resolve(PLUGIN);
    final StringBuilder config =
        new StringBuilder("import tsParser from \"%s\";\n".formatted(parser.toUri()));
    config
        .append("import tsPlugin from \"%s\";\n".formatted(plugin.toUri()))
        .append("export default [\n");
    for (final Case testCase : cases) {
      config.append(entry(testCase, workspace));
    }
    return config.append("];\n").toString();
  }

  private static String entry(final Case testCase, final Path workspace) {
    return "  { files: [%s], languageOptions: { %sparser: tsParser, parserOptions: "
            .formatted(
                TypeScriptEslintOptions.json(testCase.path()),
                TypeScriptParityModules.sourceType(testCase.row()))
        + "{ project: \"./%s\", tsconfigRootDir: %s } }, plugins: { \"@typescript-eslint\": tsPlugin },"
            .formatted(TSCONFIG, TypeScriptEslintOptions.json(workspace.toString()))
        + " rules: { %s: [\"error\"%s] } },\n"
            .formatted(
                TypeScriptEslintOptions.json(PREFIX + testCase.row()),
                TypeScriptEslintOptions.options(testCase.options()));
  }

  private static List<String> run(final Path workspace) throws IOException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    final Process process =
        new ProcessBuilder(binary, "-c", CONFIG, "-f", "./" + FORMATTER, ".")
            .directory(workspace.toFile())
            .start();
    final String stdout =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final String root = workspace.toRealPath() + "/";
    return stdout.lines().map(line -> line.replace(root, "")).toList();
  }

  private static String ruleKey(final Case testCase) {
    return testCase.path() + "#" + PREFIX + testCase.row();
  }

  /** Compares the lines per file and rule with the fixtures' expected lines. */
  private static List<String> compare(final List<Case> cases, final List<String> output) {
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
    final List<String> problems = new ArrayList<>();
    cases.forEach(testCase -> problemOf(testCase, found).ifPresent(problems::add));
    return problems;
  }

  private static Optional<String> problemOf(
      final Case testCase, final Map<String, Set<String>> found) {
    final Set<String> expected = new TreeSet<>(testCase.expected());
    final Set<String> actual = found.getOrDefault(ruleKey(testCase), new TreeSet<>());
    if (actual.equals(expected)) {
      return Optional.empty();
    }
    return Optional.of(
        testCase.path()
            + " typescript-eslint lines "
            + actual
            + " but fixture expects "
            + expected);
  }
}
