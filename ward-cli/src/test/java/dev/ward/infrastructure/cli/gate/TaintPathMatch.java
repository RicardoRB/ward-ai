package dev.ward.infrastructure.cli.gate;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.TaintCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Optional;

/** Runs the taint check on the Java fixture of the report tests, through lowering. */
final class TaintPathMatch {

  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "source", Optional.empty(), Optional.empty(), List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "sink", Optional.empty(), Optional.empty(), List.of(0))),
          List.of(),
          List.of());

  private TaintPathMatch() {}

  /** Lowers the fixture source and runs the taint check on it. */
  static List<RuleMatch> match() {
    final String source =
        "package fixture;\n\nimport java.sql.Statement;\n\nclass Flow {\n"
            + "  void run(Statement st, String text) {\n"
            + "    String raw = source();\n"
            + "    String query = \"select \" + raw;\n"
            + "    sink(query);\n"
            + "  }\n}\n";
    final SourceUnit unit = new SourceUnit(TaintPathFixtures.FILE, AnalysisLanguage.JAVA, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    final ControlFlowResult lowered = new JavaControlFlow().lower(unit, parsed.tree());
    final FlowView view = new FlowView(lowered.graphs());
    return new TaintCheck(SPEC, "untrusted data from {source} reaches {sink}")
        .matches(parsed.tree(), view);
  }
}
