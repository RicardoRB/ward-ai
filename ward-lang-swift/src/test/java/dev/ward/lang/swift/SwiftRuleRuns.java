package dev.ward.lang.swift;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Loads the Swift rule file and runs rules over source text through the engine. */
final class SwiftRuleRuns {

  static final String RULE_FILE = "rules/swift-structural.toml";
  static final String PREFIX = "ward:swift/";

  private SwiftRuleRuns() {}

  /** Runs the rule with {@code rule} as its short name over {@code source}. */
  static RuleRunResult run(final String rule, final String source) {
    final RuleDefinition definition =
        rules().stream()
            .filter(candidate -> candidate.id().equals(PREFIX + rule))
            .findFirst()
            .orElseThrow();
    final SourceUnit unit = new SourceUnit("Sample.swift", AnalysisLanguage.SWIFT, source);
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.SWIFT, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    return new RuleEngine(new MetricMatcher())
        .run(List.of(unit), List.of(support), List.of(definition));
  }

  static List<RuleDefinition> rules() {
    try (InputStream stream = SwiftRuleRuns.class.getResourceAsStream("/" + RULE_FILE)) {
      final TomlParseResult toml =
          Toml.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      final TomlArray rows = toml.getArray("rules");
      final List<RuleDefinition> rules = new ArrayList<>();
      for (int index = 0; index < rows.size(); index++) {
        rules.add(definition(rows.getTable(index)));
      }
      return rules;
    } catch (final IOException exception) {
      throw new IllegalStateException("cannot read " + RULE_FILE, exception);
    }
  }

  private static RuleDefinition definition(final TomlTable row) {
    return new RuleDefinition(
        row.getString("id"),
        AnalysisLanguage.SWIFT,
        RuleCapability.valueOf(row.getString("capability")),
        AnalysisSeverity.valueOf(row.getString("severity").toUpperCase(Locale.ROOT)),
        row.getString("body"));
  }

  static String read(final String resource) throws IOException {
    try (InputStream stream = SwiftRuleRuns.class.getResourceAsStream("/" + resource)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
