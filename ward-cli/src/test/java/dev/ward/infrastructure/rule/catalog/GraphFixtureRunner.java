package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.python.PythonDependencyResolver;
import dev.ward.analysis.domain.python.PythonFileSummarizer;
import dev.ward.analysis.domain.python.PythonProjectLayout;
import dev.ward.analysis.domain.rule.GraphRules;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.List;

/** Runs a graph rule over a project fixture of Python modules through the dependency graph. */
final class GraphFixtureRunner {

  private GraphFixtureRunner() {}

  /** The findings of a graph rule over the Python modules of one project fixture, in its root. */
  static List<AnalysisFinding> run(
      final List<FrontendSupport> frontends,
      final RuleDefinition rule,
      final List<SourceUnit> units) {
    final FrontendSupport python =
        frontends.stream()
            .filter(support -> support.language() == AnalysisLanguage.PYTHON)
            .findFirst()
            .orElseThrow();
    final List<FileSummary> summaries = new ArrayList<>();
    for (final SourceUnit unit : units) {
      summaries.add(
          new PythonFileSummarizer().summarize(unit, python.frontend().parse(unit).tree()));
    }
    final List<DependencyEdge> edges =
        new PythonDependencyResolver(new PythonProjectLayout(List.of(""))).resolve(summaries);
    return GraphRules.compile(rule).matches(new DependencyGraph(edges)).stream()
        .map(
            match ->
                new AnalysisFinding(
                    rule.id(),
                    rule.severity(),
                    match.match().message(),
                    match.file(),
                    match.match().span().startLine(),
                    match.match().span().startColumn(),
                    null,
                    List.of()))
        .toList();
  }
}
