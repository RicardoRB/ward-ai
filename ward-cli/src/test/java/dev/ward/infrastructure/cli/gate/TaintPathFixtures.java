package dev.ward.infrastructure.cli.gate;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.application.gate.AnalysisFindingMapper;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Builds the Java fixture's finding through the real pipeline, and renders it. */
final class TaintPathFixtures {

  /** The file the fixture's findings are reported in. */
  static final String FILE = "src/Flow.java";

  /** Where the fixture's sink call is. */
  static final int SINK_LINE = 9;

  static final int SINK_COLUMN = 5;

  private TaintPathFixtures() {}

  /** The JSON report of one finding, with or without its path. */
  static String json(final boolean withPath) {
    return new JsonReportRenderer(new ForyJsonCodec()).render(report(List.of(finding(withPath))));
  }

  /** The JSON report of the given finding. */
  static String jsonOf(final Finding finding) {
    return new JsonReportRenderer(new ForyJsonCodec()).render(report(List.of(finding)));
  }

  /** The SARIF report of one finding, with or without its path. */
  static String sarif(final boolean withPath) {
    return new SarifReportRenderer(new ForyJsonCodec()).render(report(List.of(finding(withPath))));
  }

  /**
   * The gate finding: the fixture's taint match mapped the way the gate maps it, or a plain one.
   */
  static Finding finding(final boolean withPath) {
    if (!withPath) {
      return new Finding(
          "ward",
          "ward",
          "ward:java/taint",
          FindingSeverity.ERROR,
          "plain",
          FILE,
          SINK_LINE,
          SINK_COLUMN,
          null,
          List.of());
    }
    final RuleMatch match = TaintPathMatch.match().get(0);
    final AnalysisFinding analysis =
        new AnalysisFinding(
            "ward:java/taint",
            AnalysisSeverity.ERROR,
            match.message(),
            FILE,
            match.span().startLine(),
            match.span().startColumn(),
            null,
            match.path());
    return new AnalysisFindingMapper().map("ward", "ward", List.of(analysis)).get(0);
  }

  private static CheckReport report(final List<Finding> findings) {
    final Project project =
        new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final GateResult result =
        GateResult.builder("ward", GateStatus.FAILED)
            .command(List.of("x"))
            .exitCode(1)
            .duration(Duration.ZERO)
            .stdout("log")
            .stderr("")
            .build()
            .withFindings(GateFindings.of(findings));
    return new CheckReport(project, List.of(result));
  }
}
