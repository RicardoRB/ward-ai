package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs the unused-definition rules over the whole project. A definition is unused only if no file
 * spells its name, so the rules need every summary and run once per run, after the per-unit cache,
 * like the duplicate-code rules. Each language chooses what its rules report through an {@link
 * UnusedDefinitionStrategy}.
 *
 * <p>shortcut: the checks are by name and by file name, with no entry-point tables or string
 * lookups, so a definition reached only through {@code getattr} or a packaging entry point is
 * reported; upgrade by reading {@code pyproject.toml} entry points.
 */
public final class UnusedDefinitionFindings {

  /** The rule id for unused functions. */
  public static final String FUNCTION_ID = PythonUnusedDefinitions.FUNCTION_ID;

  /** The rule id for unused classes. */
  public static final String CLASS_ID = PythonUnusedDefinitions.CLASS_ID;

  private UnusedDefinitionFindings() {}

  /** Whether {@code definition} is one of the unused-definition rules. */
  public static boolean isUnusedDefinitionRule(final RuleDefinition definition) {
    return binding(definition).isPresent();
  }

  /**
   * The findings of every unused-definition rule among {@code rules}, over {@code summaries}.
   *
   * @throws NullPointerException if an argument is missing
   */
  public static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules, final Map<String, FileSummary> summaries) {
    Objects.requireNonNull(summaries, "summaries");
    final Set<String> used = new HashSet<>();
    final Set<String> assigned = new HashSet<>();
    summaries
        .values()
        .forEach(
            summary -> {
              used.addAll(summary.usedNames());
              assigned.addAll(summary.assignedNames());
            });
    final Names names = new Names(used, assigned);
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final RuleDefinition rule : rules) {
      final Optional<Binding> binding = binding(rule);
      if (binding.isPresent()) {
        findings.addAll(ruleFindings(rule, binding.get(), new TreeMap<>(summaries), names));
      }
    }
    return List.copyOf(findings);
  }

  /** The strategy and kind of the rule, if it is an unused-definition rule of its language. */
  private static Optional<Binding> binding(final RuleDefinition rule) {
    for (final AnalysisLanguage language : rule.languages()) {
      final Optional<UnusedDefinitionStrategy> strategy =
          UnusedDefinitionStrategies.forLanguage(language);
      final Optional<UnusedDefinitionKind> kind =
          strategy.flatMap(found -> found.kindOf(rule.id()));
      if (strategy.isPresent() && kind.isPresent()) {
        return Optional.of(new Binding(strategy.get(), kind.get()));
      }
    }
    return Optional.empty();
  }

  private static List<AnalysisFinding> ruleFindings(
      final RuleDefinition rule,
      final Binding binding,
      final Map<String, FileSummary> summaries,
      final Names names) {
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final FileSummary summary : summaries.values()) {
      if (binding.strategy().scans(summary.path())) {
        summary.declared().stream()
            .filter(symbol -> reportable(symbol, binding, names))
            .forEach(symbol -> findings.add(finding(rule, summary.path(), symbol, binding.kind())));
      }
    }
    return findings;
  }

  private static boolean reportable(final Symbol symbol, final Binding binding, final Names names) {
    final UnusedDefinitionKind kind = binding.kind();
    return kind.symbolKind().equals(symbol.kind())
        && !names.used().contains(symbol.name())
        && (!kind.assignedOnly() || names.assigned().contains(symbol.name()))
        && !binding.strategy().exempt(symbol);
  }

  private static AnalysisFinding finding(
      final RuleDefinition rule,
      final String path,
      final Symbol symbol,
      final UnusedDefinitionKind kind) {
    return new AnalysisFinding(
        rule.id(),
        rule.severity(),
        kind.message(symbol.name()),
        path,
        symbol.span().startLine(),
        symbol.span().startColumn(),
        null,
        List.of());
  }

  /** A rule's strategy and the kind it reports. */
  private record Binding(UnusedDefinitionStrategy strategy, UnusedDefinitionKind kind) {}

  /** The names the whole project uses and the names it assigns. */
  private record Names(Set<String> used, Set<String> assigned) {}
}
