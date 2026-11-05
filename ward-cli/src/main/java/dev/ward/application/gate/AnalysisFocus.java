package dev.ward.application.gate;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the findings of a narrowed {@code ward} run that touch the changed files. A structural
 * finding is kept when its file changed. A finding of a rule that reads dependencies is kept when
 * its file changed or depends on a changed file, so a dependent's violation of an edge to a changed
 * file still shows. Every kept finding is one the full run reports unchanged.
 */
final class AnalysisFocus {
  private final Set<String> paths;
  private final Set<String> dependencyRules;
  private final DependencyGraph graph;

  /** Creates the filter for the root-relative changed {@code paths} of a run over {@code rules}. */
  AnalysisFocus(
      final List<String> paths, final List<RuleDefinition> rules, final DependencyGraph graph) {
    this.paths = Set.copyOf(paths);
    this.dependencyRules =
        rules.stream()
            .filter(rule -> rule.capability() == RuleCapability.SYMBOLS)
            .map(RuleDefinition::id)
            .collect(Collectors.toUnmodifiableSet());
    this.graph = graph;
  }

  /** Returns the findings that touch the changed paths, in their order. */
  List<AnalysisFinding> keep(final List<AnalysisFinding> findings) {
    return findings.stream().filter(this::touches).toList();
  }

  private boolean touches(final AnalysisFinding finding) {
    final String file = finding.file();
    if (file == null) {
      return false;
    }
    return paths.contains(file)
        || dependencyRules.contains(finding.ruleId()) && dependsOnFocus(file);
  }

  private boolean dependsOnFocus(final String file) {
    return graph.outgoingEdgesOf(file).stream().anyMatch(edge -> paths.contains(edge.to()));
  }
}
