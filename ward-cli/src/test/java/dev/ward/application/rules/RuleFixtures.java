package dev.ward.application.rules;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.domain.rules.RuleSelectionFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The catalog, the rule file and the body reader that the {@code ward rules} tests share. */
final class RuleFixtures {
  static final String METHOD = "ward:java/method-length";
  static final String FIELD = "ward:java/no-field-di";
  static final String KOTLIN = "ward:kotlin/duplicate-code";
  static final String STREAMS = "ward:java/no-standard-streams";

  private RuleFixtures() {}

  /** The four rules of the tests: two standard, one strict, one with no preset. */
  static List<RuleDefinition> catalog() {
    return List.of(
        rule(
            METHOD,
            AnalysisLanguage.JAVA,
            "sizes",
            Set.of(RulePreset.STANDARD),
            List.of(RuleOption.integer("max", 1))),
        rule(FIELD, AnalysisLanguage.JAVA, "architecture", Set.of(RulePreset.STRICT), List.of()),
        rule(
            KOTLIN, AnalysisLanguage.KOTLIN, "duplication", Set.of(RulePreset.STANDARD), List.of()),
        rule(STREAMS, AnalysisLanguage.JAVA, "architecture", Set.of(), List.of()));
  }

  /** A standard-preset Java rule in the category, for the group tests. */
  static RuleDefinition categorised(final String id, final String category) {
    return rule(id, AnalysisLanguage.JAVA, category, Set.of(RulePreset.STANDARD), List.of());
  }

  /** A body reader whose description is the body itself. */
  static RuleBodyReader bodies() {
    return body -> java.util.Map.of("message", "text of " + body);
  }

  /** A selection file held in memory. */
  static final class FakeFile implements RuleSelectionFile {
    Optional<String> text = Optional.empty();
    String written = "";
    int writes;

    @Override
    public Optional<String> read(final Path root) {
      return text;
    }

    @Override
    public void save(final Path root, final String value) {
      written = value;
      text = Optional.of(value);
      writes++;
    }
  }

  /** A catalog whose configuration file is set by the test. */
  static final class FakeSource implements AnalysisRuleSource {
    List<RuleDefinition> rules = catalog();
    Optional<RuleConfigFile> configured = Optional.empty();

    @Override
    public AnalysisRuleLoad load() {
      return new AnalysisRuleLoad(rules, List.of());
    }

    @Override
    public Optional<RuleConfigFile> configuration(final Path root, final String file) {
      return configured;
    }
  }

  /** A configuration file that includes {@link #FIELD} and excludes {@link #METHOD}. */
  static RuleConfigFile configuration() {
    return new RuleConfigFile(
        RuleConfigFile.DEFAULT_NAME,
        new RuleConfigFile.Select(
            null, List.of(), List.of(), List.of("java.no-field-di"), List.of("java.method-length")),
        java.util.Map.of(),
        List.of());
  }

  private static RuleDefinition rule(
      final String id,
      final AnalysisLanguage language,
      final String category,
      final Set<RulePreset> presets,
      final List<RuleOption> options) {
    return new RuleDefinition(
        id,
        Set.of(language),
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        "body-" + id,
        category,
        presets,
        options,
        0);
  }
}
