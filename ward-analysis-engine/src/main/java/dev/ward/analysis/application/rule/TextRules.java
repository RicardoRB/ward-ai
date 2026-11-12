package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.TextCompiledRule;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code TEXT} rules of one run, compiled once. A rule that cannot be compiled, or cannot run
 * on a file, is recorded as an execution error with the reason its check gives, and the run
 * continues. Findings come back sorted.
 */
public final class TextRules {
  private final List<TextCompiledRule> rules = new ArrayList<>();
  private final RuleRunCollector compiled = new RuleRunCollector();

  /** Compiles the {@code TEXT} rules among {@code definitions} with {@code matcher}. */
  public TextRules(final RuleMatcher matcher, final List<RuleDefinition> definitions) {
    for (final RuleDefinition definition : definitions) {
      if (definition.capability() == RuleCapability.TEXT) {
        compile(matcher, definition);
      }
    }
  }

  /** Runs the rules over {@code files}; the findings come back sorted. */
  public RuleRunResult run(final List<TextFile> files) {
    final RuleRunCollector collector = new RuleRunCollector();
    compiled.result().executionErrors().forEach(collector::recordError);
    for (final TextCompiledRule rule : rules) {
      files.forEach(file -> runOn(rule, file, collector));
    }
    return collector.result();
  }

  private void compile(final RuleMatcher matcher, final RuleDefinition definition) {
    try {
      final CompiledRule rule = matcher.compile(definition);
      if (rule instanceof TextCompiledRule text) {
        rules.add(text);
      } else {
        compiled.recordError(
            new RuleExecutionError(definition.id(), null, "the rule is not a text rule"));
      }
    } catch (final AnalysisFailedException exception) {
      compiled.recordError(
          new RuleExecutionError(definition.id(), null, "the rule could not be compiled"));
    }
  }

  private static void runOn(
      final TextCompiledRule rule, final TextFile file, final RuleRunCollector collector) {
    try {
      rule.textMatches(file)
          .forEach(
              match -> collector.finding(RuleFindings.at(rule.definition(), file.path(), match)));
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(rule.definition().id(), file.path(), exception.getMessage()));
    }
  }
}
