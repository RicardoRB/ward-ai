package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds the rules of {@code ward-architecture.toml} that cannot be evaluated over a run, which fail
 * the gate instead of passing: a rule that selects no file unless it allows that, and a dependency
 * rule over a file whose language has no dependency summary in the run.
 */
public final class ArchitectureRuleCheck {

  private ArchitectureRuleCheck() {}

  /** Returns one error per rule of {@code rules} that cannot be evaluated over {@code project}. */
  public static List<RuleExecutionError> errors(
      final List<ArchitectureRule> rules, final ProjectFiles project) {
    final List<RuleExecutionError> errors = new ArrayList<>();
    for (final ArchitectureRule rule : rules) {
      error(rule, project).ifPresent(errors::add);
    }
    return List.copyOf(errors);
  }

  private static Optional<RuleExecutionError> error(
      final ArchitectureRule rule, final ProjectFiles project) {
    final Predicate<String> selected = rule.select().matcher();
    final List<String> files = sorted(project.sources(), selected);
    if (files.isEmpty()) {
      return rule.allowEmpty()
          ? Optional.empty()
          : Optional.of(failure(rule, "rule '" + rule.name() + "' selects no file"));
    }
    if (!rule.constraint().kind().readsEdges()) {
      return Optional.empty();
    }
    final List<String> related = new ArrayList<>(files);
    related.addAll(sorted(project.sources(), rule.constraint().target().matcher()));
    return related.stream()
        .filter(path -> !project.files().contains(path))
        .findFirst()
        .map(path -> failure(rule, noEdges(rule, path, project.sources().get(path))));
  }

  private static String noEdges(
      final ArchitectureRule rule, final String path, final AnalysisLanguage language) {
    return "rule '"
        + rule.name()
        + "' cannot be evaluated: the run has no dependency edges for "
        + language
        + " files ("
        + path
        + ")";
  }

  private static List<String> sorted(
      final Map<String, AnalysisLanguage> sources, final Predicate<String> selected) {
    return sources.keySet().stream().filter(selected).sorted().toList();
  }

  private static RuleExecutionError failure(final ArchitectureRule rule, final String reason) {
    return new RuleExecutionError(rule.constraint().kind().ruleId(), null, reason);
  }
}
