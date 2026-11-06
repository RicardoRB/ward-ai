package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** Runs the example rules of {@code rules-examples/} over a source and reads the findings. */
final class ExampleRuns {

  private ExampleRuns() {}

  /** Returns the findings of the language's example rules over one file. */
  /** The 1-based line of the first occurrence of each marker in {@code source}, in line order. */
  static List<Integer> at(final String source, final String... markers) {
    final List<String> lines = source.lines().toList();
    return Arrays.stream(markers).map(marker -> 1 + indexOfLine(lines, marker)).sorted().toList();
  }

  static int indexOfLine(final List<String> lines, final String marker) {
    return IntStream.range(0, lines.size())
        .filter(index -> lines.get(index).contains(marker))
        .findFirst()
        .orElseThrow();
  }

  static List<RuleDefinition> rules(final String language) {
    return new ClasspathRuleSource(List.of("rules-examples/" + language + ".toml")).load().rules();
  }

  static List<AnalysisFinding> run(
      final AnalysisLanguage language, final String name, final String source) {
    final FrontendSupport frontend =
        new FrontendSupport(language, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    final String path = language == AnalysisLanguage.JAVA ? "src/main/java/A.java" : "app/mod.py";
    return new RuleEngine(new DeclarativeRuleMatcher())
        .run(List.of(new SourceUnit(path, language, source)), List.of(frontend), rules(name))
        .findings();
  }

  static List<Integer> lines(final List<AnalysisFinding> findings, final String ruleId) {
    return findings.stream()
        .filter(finding -> finding.ruleId().equals(ruleId))
        .map(AnalysisFinding::line)
        .sorted()
        .toList();
  }

  static List<String> messages(final List<AnalysisFinding> findings, final String ruleId) {
    return findings.stream()
        .filter(finding -> finding.ruleId().equals(ruleId))
        .map(AnalysisFinding::message)
        .toList();
  }
}
