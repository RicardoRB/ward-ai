package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chooses the rules of a {@code ward} check. Without a rule configuration file the check runs the
 * rules its {@code rules} list names, or all of them, as it always did. With one, the file selects
 * and tunes the rules, and a {@code rules} list in {@code ward.toml} is a problem.
 */
final class ConfiguredRuleSetup implements RuleSetup {
  private static final Logger LOGGER = Logger.getLogger(ConfiguredRuleSetup.class.getName());

  private final String checkName;
  private final String ruleFile;
  private final Choices choices;
  private final AnalysisRuleSource source;

  /**
   * The rules a check can run.
   *
   * @param listed the rules {@code ward.toml} selects, or all of {@code available} when it names
   *     none
   * @param listsRules whether {@code ward.toml} sets {@code rules}
   * @param available every rule that exists
   * @param architecture the rules built from the check's architecture tables, always added
   */
  record Choices(
      List<RuleDefinition> listed,
      boolean listsRules,
      List<RuleDefinition> available,
      List<RuleDefinition> architecture) {

    /** Copies the lists. */
    Choices {
      listed = List.copyOf(listed);
      available = List.copyOf(available);
      architecture = List.copyOf(architecture);
    }
  }

  /**
   * Creates the setup of one check.
   *
   * @param checkName the check's name, for messages
   * @param ruleFile the {@code rules-file} path, or {@code null} for {@code ward-rules.toml}
   * @param choices the rules the check can run
   * @param source reads the configuration file
   */
  ConfiguredRuleSetup(
      final String checkName,
      final String ruleFile,
      final Choices choices,
      final AnalysisRuleSource source) {
    this.checkName = checkName;
    this.ruleFile = ruleFile;
    this.choices = choices;
    this.source = source;
  }

  @Override
  public RuleSet resolve(final Path root, final int javaRelease) {
    final Optional<RuleConfigFile> file = source.configuration(root, ruleFile);
    if (file.isEmpty()) {
      return RuleSet.of(concat(withDefaults(choices.listed(), javaRelease)));
    }
    if (choices.listsRules()) {
      final String name = file.get().name();
      return new RuleSet(
          List.of(),
          Map.of(),
          List.of(
              "ward.toml: [checks."
                  + checkName
                  + "] rules: the list is set in ward.toml and "
                  + name
                  + " exists; remove it and select rules in "
                  + name
                  + " under [select]"));
    }
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=rule-config-file check=" + checkName + " file=" + file.get().name());
    }
    final RuleSet resolved =
        RuleConfigResolver.resolve(file.get(), choices.available(), javaRelease);
    return new RuleSet(concat(resolved.rules()), resolved.excludePaths(), resolved.problems());
  }

  private static List<RuleDefinition> withDefaults(
      final List<RuleDefinition> rules, final int javaRelease) {
    return rules.stream().map(rule -> JavaRelease.withDefault(rule, javaRelease)).toList();
  }

  private List<RuleDefinition> concat(final List<RuleDefinition> rules) {
    final List<RuleDefinition> all = new ArrayList<>(rules);
    all.addAll(choices.architecture());
    return all;
  }
}
