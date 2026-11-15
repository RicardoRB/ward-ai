package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Lowers Kotlin source for the control-flow, access and flow-site tests. */
final class KotlinFlowFixtures {

  private KotlinFlowFixtures() {}

  static ControlFlowResult lower(final String source) {
    final SourceUnit unit = new SourceUnit("src/C.kt", AnalysisLanguage.KOTLIN, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new KotlinControlFlow().lower(unit, parsed.tree());
  }

  /** The graph of the body called {@code body}, such as {@code C#f(1)}. */
  static ControlFlowGraph graph(final String source, final String body) {
    return lower(source).graphs().stream()
        .filter(candidate -> candidate.body().equals(body))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no body " + body));
  }

  /** The sites of a function {@code f} of a class {@code C} with {@code count} parameters. */
  static List<FlowSite> sites(final String source, final int count) {
    return graph("class C {\n" + source + "\n}\n", "C#f(" + count + ")").blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .toList();
  }

  /** The accesses of the graph as {@code kind:name}, in block order. */
  static List<String> accesses(final ControlFlowGraph graph) {
    return graph.blocks().stream()
        .map(BasicBlock::accesses)
        .flatMap(List::stream)
        .map(access -> label(access.kind()) + ":" + access.name())
        .toList();
  }

  static List<VariableAccess> rawAccesses(final ControlFlowGraph graph) {
    return graph.blocks().stream().map(BasicBlock::accesses).flatMap(List::stream).toList();
  }

  private static String label(final AccessKind kind) {
    return kind.name().substring(0, 1).toLowerCase(java.util.Locale.ROOT);
  }
}
