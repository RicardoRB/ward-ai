package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Optional;

/** Lowers TypeScript and JavaScript sources for the taint tests, and reads their paths. */
final class NodeTaintFixtures {

  /** The fake specification: an unqualified sink, a qualified sanitizer and the sources. */
  static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "source", Optional.empty(), Optional.empty(), List.of()),
              new TaintEntry(
                  FlowSiteKind.MEMBER_READ,
                  "query",
                  Optional.empty(),
                  Optional.of("req"),
                  List.of()),
              new TaintEntry(
                  FlowSiteKind.PARAMETER,
                  "Untrusted",
                  Optional.empty(),
                  Optional.empty(),
                  List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "sink", Optional.empty(), Optional.empty(), List.of(0))),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "clean", Optional.empty(), Optional.of("san"), List.of())),
          List.of());

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

  /** Lowers a source and runs the file-level analysis on all of its graphs. */
  static List<dev.ward.analysis.domain.flow.taint.TaintFinding> runFile(
      final AnalysisLanguage language, final String source) {
    final String file = language == AnalysisLanguage.TYPESCRIPT ? "src/a.ts" : "src/a.js";
    final SourceUnit unit = new SourceUnit(file, language, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return dev.ward.analysis.domain.flow.taint.TaintFile.run(
        new NodeControlFlow().lower(unit, parsed.tree()).graphs(), SPEC);
  }

  /** The messages of the steps of a path, in order. */
  static List<String> messages(final List<FlowStep> path) {
    return path.stream().map(FlowStep::message).toList();
  }
}
