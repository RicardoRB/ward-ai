package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builders of rules, selectors, graphs and definitions for the architecture rule tests. */
final class ArchitectureFixtures {
  static final String DOMAIN_DIR = "domain";
  static final int IMPORT_LINE = 4;

  private ArchitectureFixtures() {}

  static List<GraphMatch> dependencies(final ArchitectureRule rule, final DependencyEdge... edges) {
    return GraphRules.compile(definition(DependencyArchitectureRule.ID, List.of(rule)))
        .matches(new DependencyGraph(List.of(edges)));
  }

  static List<GraphMatch> files(
      final String id, final ArchitectureRule rule, final Map<String, AnalysisLanguage> sources) {
    return GraphRules.compile(definition(id, List.of(rule)))
        .matches(new DependencyGraph(List.of()), project(sources));
  }

  static ProjectFiles project(final Map<String, AnalysisLanguage> sources) {
    return new ProjectFiles(sources.keySet(), Set.of(), sources);
  }

  static RuleDefinition definition(final String id, final List<ArchitectureRule> rules) {
    return new RuleDefinition(
        id,
        Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.PYTHON),
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        ArchitectureRuleBody.encode(rules));
  }

  static ArchitectureRule rule(
      final String name,
      final ArchitectureSelector select,
      final Kind kind,
      final ArchitectureSelector target) {
    return new ArchitectureRule(
        name, select, new ArchitectureConstraint(kind, target, null), "the reason", false);
  }

  static ArchitectureSelector dirs(final String... names) {
    return new ArchitectureSelector(List.of(), List.of(names), List.of());
  }

  static ArchitectureSelector paths(final String... globs) {
    return new ArchitectureSelector(List.of(globs), List.of(), List.of());
  }

  static DependencyEdge edge(final String from, final String to) {
    final Span span = new Span(IMPORT_LINE, 1, IMPORT_LINE, 2, 40, 41);
    return new DependencyEdge(from, to, new Reference(to, span));
  }
}
