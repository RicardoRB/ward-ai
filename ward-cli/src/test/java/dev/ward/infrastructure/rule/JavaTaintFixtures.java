package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Optional;

/** The fake specification and the lowering helpers of the Java taint tests. */
final class JavaTaintFixtures {

  /** The specification: sources, sinks and a sanitizer that the tests name. */
  static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "source", Optional.empty(), Optional.empty(), List.of()),
              new TaintEntry(
                  FlowSiteKind.PARAMETER,
                  "Untrusted",
                  Optional.empty(),
                  Optional.empty(),
                  List.of()),
              new TaintEntry(
                  FlowSiteKind.MEMBER_READ,
                  "query",
                  Optional.empty(),
                  Optional.of("req"),
                  List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "sink", Optional.empty(), Optional.empty(), List.of(0)),
              new TaintEntry(
                  FlowSiteKind.CALL,
                  "executeQuery",
                  Optional.of("java.sql.Statement"),
                  Optional.empty(),
                  List.of(0))),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "clean", Optional.empty(), Optional.empty(), List.of())),
          List.of());

  private JavaTaintFixtures() {}

  /** Runs the analysis on a method body whose method is {@code void run()}. */
  static List<TaintFinding> run(final String body) {
    return run(body, "void run()");
  }

  /** Runs the analysis on a method body under the given method signature. */
  static List<TaintFinding> run(final String body, final String signature) {
    final String source =
        "import java.sql.Statement;\n"
            + "class Flow {\n"
            + "  "
            + signature
            + " {\n    "
            + body
            + "\n  }\n}\n";
    return TaintAnalysis.run(graph(source), SPEC);
  }

  /** Lowers a source and returns the graph of its {@code run} method. */
  static ControlFlowGraph graph(final String source) {
    final SourceUnit unit = new SourceUnit("src/Flow.java", AnalysisLanguage.JAVA, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    final ControlFlowResult result = new JavaControlFlow().lower(unit, parsed.tree());
    return result.graphs().stream()
        .filter(graph -> graph.body().contains("#run("))
        .findFirst()
        .orElseThrow();
  }

  /** The messages of the steps of a path, in order. */
  static List<String> messages(final List<FlowStep> path) {
    return path.stream().map(FlowStep::message).toList();
  }
}
