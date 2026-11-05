package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Lowers TypeScript and JavaScript sources and looks up their accesses for the access tests. */
final class NodeAccessFixtures {

  private NodeAccessFixtures() {}

  static List<VariableAccess> accessesOf(final ControlFlowGraph graph) {
    return graph.blocks().stream().flatMap(block -> block.accesses().stream()).toList();
  }

  static List<String> kinds(final List<VariableAccess> accesses) {
    return accesses.stream().map(access -> access.kind() + " " + access.name()).toList();
  }

  static List<String> kinds(final ControlFlowGraph graph) {
    return kinds(accessesOf(graph));
  }

  static ControlFlowGraph typescript(final String source, final String name) {
    return lower(source, AnalysisLanguage.TYPESCRIPT, name);
  }

  static ControlFlowGraph javaScript(final String source, final String name) {
    return lower(source, AnalysisLanguage.JAVASCRIPT, name);
  }

  static ControlFlowGraph lower(
      final String source, final AnalysisLanguage language, final String name) {
    final String path = language == AnalysisLanguage.JAVASCRIPT ? "src/a.js" : "src/a.ts";
    final SourceUnit unit = new SourceUnit(path, language, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    final ControlFlowResult result = new NodeControlFlow().lower(unit, parsed.tree());
    return result.graphs().stream()
        .filter(graph -> graph.body().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graph " + name));
  }
}
