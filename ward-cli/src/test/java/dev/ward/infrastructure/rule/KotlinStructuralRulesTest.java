package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the packaged Kotlin rules over the fixtures, one violating and one passing set per rule. */
class KotlinStructuralRulesTest {

  private static final String FIXTURES = "analysis/kotlin/";
  private static final String VIOLATING = "violating";
  private static final String PASSING = "passing";
  private static final String MODULES = "fixtures/gradle-kotlin-modules";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file-length",
        "function-length",
        "parameter-count",
        "cyclomatic-complexity",
        "type-name",
        "function-name",
        "property-name",
        "cognitive-complexity"
      })
  void violatingFixtureReportsItsRule(final String rule) throws IOException, URISyntaxException {
    assertThat(findingsOf(rule, VIOLATING)).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file-length",
        "function-length",
        "parameter-count",
        "cyclomatic-complexity",
        "type-name",
        "function-name",
        "property-name",
        "cognitive-complexity"
      })
  void passingFixtureDoesNotReportItsRule(final String rule)
      throws IOException, URISyntaxException {
    assertThat(findingsOf(rule, PASSING)).isEmpty();
  }

  @Test
  void gradleKotlinModulesAreScannedWithoutExecutionErrors()
      throws IOException, URISyntaxException {
    final RuleRunResult result = run(kotlinUnits(MODULES));

    assertThat(result.executionErrors()).isEmpty();
  }

  @Test
  void repeatedRunsGiveTheSameFindings() throws IOException, URISyntaxException {
    final List<SourceUnit> units = units(FIXTURES + "file-length/" + VIOLATING);

    assertThat(run(units).findings()).isEqualTo(run(units).findings());
  }

  private static List<AnalysisFinding> findingsOf(final String rule, final String set)
      throws IOException, URISyntaxException {
    final String ruleId = "ward:kotlin/" + rule;
    return run(units(FIXTURES + rule + "/" + set)).findings().stream()
        .filter(finding -> finding.ruleId().equals(ruleId))
        .toList();
  }

  private static RuleRunResult run(final List<SourceUnit> units) {
    final List<RuleDefinition> rules = new ClasspathRuleSource().load().rules();
    final FrontendSupport kotlin =
        new FrontendSupport(
            AnalysisLanguage.KOTLIN, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    return new RuleEngine(new DeclarativeRuleMatcher()).run(units, List.of(kotlin), rules);
  }

  private static List<SourceUnit> units(final String directory)
      throws IOException, URISyntaxException {
    final URL url = KotlinStructuralRulesTest.class.getResource("/" + directory);
    if (url == null) {
      throw new IOException("missing fixture directory " + directory);
    }
    try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
      return files
          .filter(path -> path.toString().endsWith(".kt"))
          .sorted()
          .map(KotlinStructuralRulesTest::unit)
          .toList();
    }
  }

  private static List<SourceUnit> kotlinUnits(final String directory)
      throws IOException, URISyntaxException {
    final URL url = KotlinStructuralRulesTest.class.getResource("/" + directory);
    if (url == null) {
      throw new IOException("missing fixture directory " + directory);
    }
    try (Stream<Path> files = Files.walk(Path.of(url.toURI()))) {
      return files
          .filter(path -> path.toString().endsWith(".kt"))
          .sorted()
          .map(KotlinStructuralRulesTest::unit)
          .toList();
    }
  }

  private static SourceUnit unit(final Path path) {
    try {
      return new SourceUnit(
          path.getFileName().toString(), AnalysisLanguage.KOTLIN, Files.readString(path));
    } catch (final IOException exception) {
      throw new IllegalStateException("cannot read fixture " + path, exception);
    }
  }
}
