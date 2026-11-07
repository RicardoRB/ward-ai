package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.RuleDefinitions;
import dev.ward.analysis.application.rule.RuleFileProblems;
import dev.ward.analysis.application.rule.RuleFileProvider;
import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Loads analysis rule files packaged as classpath resources. A file is TOML with one {@code
 * [[rules]]} table per rule, each holding {@code id}, {@code language}, {@code capability}, {@code
 * severity} and {@code body}. A file that cannot be used is skipped as a whole and reported.
 */
public final class ClasspathRuleSource implements AnalysisRuleSource {

  private final List<String> resources;

  /** Creates the source that loads the packaged rule files of the bundled language modules. */
  public ClasspathRuleSource() {
    this(Set.copyOf(BundledLanguages.ruleFiles()));
  }

  /** Creates the source that loads the rule files of every given language module, sorted. */
  @Inject
  public ClasspathRuleSource(final Set<RuleFileProvider> providers) {
    this(RuleFileProvider.sortedFiles(providers));
  }

  /** Creates the source that loads the given classpath resources, in order. */
  public ClasspathRuleSource(final List<String> resources) {
    this.resources = List.copyOf(resources);
  }

  @Override
  public AnalysisRuleLoad load() {
    final List<RuleDefinition> rules = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    for (final String resource : resources) {
      try {
        final List<RuleDefinition> file = RuleFileReader.read(resource);
        file.forEach(rule -> checkBody(resource, rule));
        RuleDefinitions.requireUnique(concat(rules, file));
        rules.addAll(file);
      } catch (final RuleConfigurationException exception) {
        errors.add(exception.getMessage());
      }
    }
    return new AnalysisRuleLoad(rules, errors);
  }

  private static List<RuleDefinition> concat(
      final List<RuleDefinition> loaded, final List<RuleDefinition> file) {
    final List<RuleDefinition> all = new ArrayList<>(loaded);
    all.addAll(file);
    return all;
  }

  /** Builds the rule once so that a body that cannot be used is reported with its file. */
  private static void checkBody(final String resource, final RuleDefinition definition) {
    try {
      BundledRuleMatchers.create().compile(definition);
    } catch (final AnalysisFailedException exception) {
      throw RuleFileProblems.problem(
          resource,
          "rule '" + definition.id() + "' cannot be used: " + exception.getMessage(),
          exception);
    }
  }
}
