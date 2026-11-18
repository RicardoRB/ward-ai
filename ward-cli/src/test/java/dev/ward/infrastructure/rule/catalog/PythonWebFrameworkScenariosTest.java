package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story #597: Python rules on web applications, and the CTFd corpus. */
class PythonWebFrameworkScenariosTest {
  private static final int SHA256_HEX = 64;
  private static final String PREFIX = "ward:python/";

  private static List<RuleDefinition> standardPreset() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(rule -> rule.covers(AnalysisLanguage.PYTHON))
        .filter(rule -> RulePreset.STANDARD.selects(rule.presets()))
        .toList();
  }

  private static List<String> reported(final String ruleName, final String source) {
    final List<RuleDefinition> rules = standardPreset();
    final List<SourceUnit> units =
        List.of(new SourceUnit("app/sample.py", AnalysisLanguage.PYTHON, source));
    final List<FrontendSupport> frontends = new RuleFixtureRunner().frontends();
    final List<AnalysisFinding> findings =
        new ArrayList<>(
            new RuleEngine(BundledRuleMatchers.create()).run(units, frontends, rules).findings()
        );
    findings.addAll(
        UnusedDefinitionFindings.findings(rules, PythonCorpus.summaries(units, frontends))
    );
    return findings.stream()
        .filter(finding -> finding.ruleId().equals(PREFIX + ruleName))
        .map(finding -> finding.line() + ":" + finding.column())
        .toList();
  }

  @Test
  @DisplayName("a test method named by the framework")
  void aTestMethodNamedByTheFramework() {
    // Given a unittest class with setUp
    final String source =
        """
        import unittest


        class CartTest(unittest.TestCase):
            def setUp(self):
                self.items = []

            def test_empty(self):
                self.assertEqual(self.items, [])
        """;

    // When the Python standard preset runs
    final List<String> found = reported("naming", source);

    // Then naming reports nothing for setUp
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("methods Django calls by name")
  void methodsDjangoCallsByName() {
    // Given a Django form with clean_<field> and an admin hook
    final String source =
        """
        from django import forms
        from django.contrib import admin


        class SignupForm(forms.Form):
            name = forms.CharField()

            def clean_name(self):
                return self.cleaned_data["name"].strip()


        class SizeFilter(admin.SimpleListFilter):
            title = "size"

            def lookups(self, request, model_admin):
                return (("big", "Big"),)
        """;

    // When unused-method runs
    final List<String> found = reported("unused-method", source);

    // Then it reports neither
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("a Flask or FastAPI corpus")
  void aFlaskOrFastApiCorpus() throws IOException {
    // Given a pinned Flask or FastAPI application
    final Path benchmark = Path.of("src/test/resources/benchmark");
    final String report = Files.readString(benchmark.resolve("flask-corpus-report.txt"));

    // When its corpus smoke test has run (-Dward.benchmark.flask-corpus, see FlaskCorpusSmokeTest)
    // Then no valid file is left without analysis (the smoke test fails otherwise)
    assertThat(FlaskCorpus.ARCHIVE_SHA256).hasSize(SHA256_HEX);
    assertThat(report).contains(FlaskCorpus.TAG).contains(FlaskCorpus.ARCHIVE_SHA256);

    // And its report, labels and cost are committed
    assertThat(benchmark.resolve("flask-corpus-labels.txt")).isNotEmptyFile();
    assertThat(benchmark.resolve("flask-corpus-cost.txt")).isNotEmptyFile();
  }
}
