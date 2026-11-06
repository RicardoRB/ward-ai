package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Runs each option case of an implemented rule: a fixture directory {@code option-<case>/} holds an
 * {@code options.toml} with the user option values, and its {@code violating.<ext>} must report
 * exactly the expected positions with those values, and {@code passing.<ext>} nothing. Every option
 * a rule declares needs at least one case, so each option is checked at a non-default value.
 */
class RuleOptionFixturesTest {

  private static final Map<String, RuleDefinition> RULES =
      CatalogRuleDefinitions.all().stream()
          .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));

  /** One option case of an implemented pair. */
  record OptionCase(RuleCatalogFixturesTest.Pair pair, Path directory) {
    @Override
    public String toString() {
      return pair + " " + directory.getFileName();
    }
  }

  static Stream<OptionCase> optionCases() throws IOException {
    final List<OptionCase> cases = new ArrayList<>();
    for (final RuleCatalogFixturesTest.Pair pair : RuleCatalogFixturesTest.pairs().toList()) {
      final RuleFixtureSet fixtures =
          RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language());
      if (!Files.isDirectory(fixtures.directory())) {
        continue;
      }
      try (Stream<Path> entries = Files.list(fixtures.directory())) {
        entries
            .filter(Files::isDirectory)
            .filter(entry -> entry.getFileName().toString().startsWith("option-"))
            .sorted()
            .forEach(entry -> cases.add(new OptionCase(pair, entry)));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("optionCases")
  void optionCaseReportsExactlyTheExpectedPositions(final OptionCase optionCase)
      throws IOException {
    final RuleDefinition rule =
        RULES.get(optionCase.pair().row().idFor(optionCase.pair().language()));
    assertThat(rule).isNotNull();
    final RuleDefinition tuned = RuleOptions.apply(rule, values(optionCase.directory()));
    final Path directory = optionCase.directory();
    final String extension = RuleFixtureSet.extension(optionCase.pair().language());
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final TypeFacts typeFacts = FixtureTypeFacts.of(Optional.empty());
    final List<String> problems = new ArrayList<>();
    problems.addAll(
        check(
            runner,
            tuned,
            directory.resolve("violating." + extension),
            expected(directory),
            typeFacts));
    problems.addAll(
        check(runner, tuned, directory.resolve("passing." + extension), List.of(), typeFacts));
    assertThat(problems).isEmpty();
  }

  /** Checks the Java naming rules; the other rules are covered by their own change. */
  @Test
  void everyDeclaredOptionOfAJavaNamingRuleHasAnOptionCase() throws IOException {
    final List<String> missing = new ArrayList<>();
    for (final RuleCatalogFixturesTest.Pair pair : RuleCatalogFixturesTest.pairs().toList()) {
      final RuleDefinition rule = RULES.get(pair.row().idFor(pair.language()));
      if (!naming(rule)) {
        continue;
      }
      for (final RuleOption option : rule.options()) {
        if (!covers(rule, option)) {
          missing.add(rule.id() + " " + option.name());
        }
      }
    }
    assertThat(missing).isEmpty();
  }

  /** The naming rules of Java, which every naming rule must cover with an option case. */
  private static boolean naming(final RuleDefinition rule) {
    return rule.id().startsWith("ward:java/")
        && !rule.id().contains("/example-")
        && "naming".equals(rule.category());
  }

  private static boolean covers(final RuleDefinition rule, final RuleOption option)
      throws IOException {
    for (final OptionCase optionCase : optionCases().toList()) {
      if (optionCase.pair().row().idFor(optionCase.pair().language()).equals(rule.id())
          && Files.isRegularFile(optionCase.directory().resolve("options.toml"))
          && values(optionCase.directory()).containsKey(option.name())) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Object> values(final Path directory) throws IOException {
    final TomlParseResult toml = Toml.parse(Files.readString(directory.resolve("options.toml")));
    final Map<String, Object> values = new HashMap<>();
    for (final String key : toml.keySet()) {
      values.put(key, toml.get(key));
    }
    return values;
  }

  private static List<String> expected(final Path directory) throws IOException {
    return Files.readAllLines(directory.resolve("expected.txt")).stream()
        .filter(line -> !line.isBlank())
        .toList();
  }

  private static List<String> check(
      final RuleFixtureRunner runner,
      final RuleDefinition rule,
      final Path file,
      final List<String> expected,
      final TypeFacts typeFacts)
      throws IOException {
    final List<AnalysisFinding> findings =
        runner.run(rule, file.getFileName().toString(), Files.readString(file), typeFacts);
    return FixtureComparison.problems(
        rule.id(),
        rule.languages().iterator().next(),
        file.getFileName().toString(),
        expected,
        findings);
  }
}
