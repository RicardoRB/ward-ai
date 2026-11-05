package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Lowers TypeScript and JavaScript sources for the taint tests, and reads their paths. */
final class NodeTaintFixtures {

  private NodeTaintFixtures() {}

  /** Lowers a source and returns the graph whose body starts with the given name. */
  static ControlFlowGraph graph(
      final AnalysisLanguage language, final String source, final String name) {
    final String file = language == AnalysisLanguage.TYPESCRIPT ? "src/a.ts" : "src/a.js";
    final SourceUnit unit = new SourceUnit(file, language, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return new NodeControlFlow()
        .lower(unit, parsed.tree()).graphs().stream()
            .filter(graph -> graph.body().startsWith(name))
            .findFirst()
            .orElseThrow();
  }

  /** The messages of the steps of a path, in order. */
  static List<String> messages(final List<FlowStep> path) {
    return path.stream().map(FlowStep::message).toList();
  }
}
