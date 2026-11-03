package dev.ward.analysis.infrastructure.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every structural rule over its violating and passing fixture, for TypeScript, ESM JavaScript
 * and CommonJS JavaScript. A violating fixture yields exactly one finding; a passing one yields
 * none.
 */
class NodeStructuralFixturesTest {

  private static final String ROOT = "fixtures/node-structural/";
  private static final List<String> METRICS =
      List.of(
          "function-length",
          "parameter-count",
          "complexity",
          "file-length",
          "naming",
          "nesting-depth");

  static Stream<Arguments> fixtures() {
    final List<Arguments> cases = new ArrayList<>();
    addDialect(cases, "ts", "ts", AnalysisLanguage.TYPESCRIPT);
    addDialect(cases, "js-esm", "mjs", AnalysisLanguage.JAVASCRIPT);
    addDialect(cases, "js-cjs", "cjs", AnalysisLanguage.JAVASCRIPT);
    return cases.stream();
  }

  private static void addDialect(
      final List<Arguments> cases,
      final String directory,
      final String extension,
      final AnalysisLanguage language) {
    for (final String metric : METRICS) {
      cases.add(Arguments.of(directory, extension, language, metric, "violating", 1));
      cases.add(Arguments.of(directory, extension, language, metric, "passing", 0));
    }
  }

  @ParameterizedTest(name = "{3} {4} in {0} gives {5} finding(s)")
  @MethodSource("fixtures")
  void fixtureYieldsExpectedFindings(
      final String directory,
      final String extension,
      final AnalysisLanguage language,
      final String metric,
      final String kind,
      final int expected)
      throws IOException {
    final String path = ROOT + directory + "/" + metric + "-" + kind + "." + extension;
    final String text = read(path);

    final RuleRunResult result = NodeRuleRuns.run(metric, language, "", text);

    assertThat(result.executionErrors()).as(path).isEmpty();
    assertThat(result.notRun()).as(path).isEmpty();
    assertThat(result.findings()).as(path).hasSize(expected);
    for (final AnalysisFinding finding : result.findings()) {
      assertThat(finding.ruleId()).isEqualTo(NodeRuleRuns.id(language, metric));
    }
  }

  private static String read(final String path) throws IOException {
    try (InputStream stream = NodeStructuralFixturesTest.class.getResourceAsStream("/" + path)) {
      assertThat(stream).as("fixture %s", path).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
