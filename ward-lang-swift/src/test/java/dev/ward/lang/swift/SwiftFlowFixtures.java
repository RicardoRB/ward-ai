package dev.ward.lang.swift;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFile;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.swift.SwiftControlFlow;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Optional;

/**
 * Lowers Swift sources for the flow tests and runs the taint analysis with a tiny specification:
 * {@code src()} and a read of {@code tainted} are sources, {@code sink(x)} guards its argument,
 * {@code clean(x)} and {@code .safe} are sanitizers, and a write to {@code arguments} of a {@code
 * Process} is a sink.
 */
final class SwiftFlowFixtures {
  static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              entry(FlowSiteKind.CALL, "src", Optional.empty(), List.of()),
              entry(FlowSiteKind.MEMBER_READ, "tainted", Optional.empty(), List.of())
          ),
          List.of(
              entry(FlowSiteKind.CALL, "sink", Optional.empty(), List.of(0)),
              entry(FlowSiteKind.MEMBER_WRITE, "arguments", Optional.of("Process"), List.of(0))
          ),
          List.of(
              entry(FlowSiteKind.CALL, "clean", Optional.empty(), List.of()),
              entry(FlowSiteKind.MEMBER_READ, "safe", Optional.empty(), List.of())
          ),
          List.of()
      );

  private SwiftFlowFixtures() {}

  private static TaintEntry entry(
      final FlowSiteKind kind,
      final String name,
      final Optional<String> receiver,
      final List<Integer> arguments
  ) {
    return new TaintEntry(kind, name, receiver, Optional.empty(), arguments);
  }

  /** The graphs of a file. */
  static List<ControlFlowGraph> graphs(final String source) {
    final SourceUnit unit = new SourceUnit("Sample.swift", AnalysisLanguage.SWIFT, source);
    return new SwiftControlFlow().lower(unit, new AntlrLanguageFrontend().parse(unit).tree())
        .graphs();
  }

  /** The names of the graphs of a file. */
  static List<String> names(final String source) {
    return graphs(source).stream().map(ControlFlowGraph::body).toList();
  }

  /** The graph named {@code name} in a file. */
  static ControlFlowGraph graph(final String source, final String name) {
    return graphs(source).stream()
        .filter(graph -> graph.body().equals(name))
        .findFirst()
        .orElseThrow();
  }

  /** The number of findings in {@code func f(a: String)} of struct C, written as {@code body}. */
  static int count(final String body) {
    return TaintAnalysis.run(
        graph("struct C {\nfunc f(a: String) {\n" + body + "\n}\n}\n", "C#f(1)"),
        SPEC
    ).size();
  }

  /** The findings of the whole file, calls between its functions followed. */
  static List<TaintFinding> file(final String source) {
    return TaintFile.run(graphs(source), SPEC);
  }
}
