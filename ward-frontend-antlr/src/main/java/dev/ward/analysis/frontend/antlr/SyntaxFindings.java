package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.ArrayList;
import java.util.List;

/** Collects the syntax errors of one parse, from the lexer and the parser, as findings. */
public final class SyntaxFindings {

  /** The rule id of every syntax finding. */
  public static final String RULE_ID = "syntax";

  private final List<AnalysisFinding> findings = new ArrayList<>();
  private String path;

  /**
   * Clears the findings and names the unit the next errors belong to.
   *
   * @param unitPath the project-root-relative path of the unit
   */
  public void start(final String unitPath) {
    findings.clear();
    path = unitPath;
  }

  /**
   * Adds one syntax error at a 1-based line and column.
   *
   * @param line the line of the error, 1-based
   * @param column the column of the error, 1-based
   * @param message the message that names what was found or expected
   */
  public void add(final int line, final int column, final String message) {
    findings.add(
        new AnalysisFinding(
            RULE_ID,
            AnalysisSeverity.ERROR,
            message,
            path,
            Math.max(1, line),
            Math.max(1, column),
            null));
  }

  /**
   * Returns the findings collected since the last {@link #start}.
   *
   * @return a copy of the findings
   */
  public List<AnalysisFinding> findings() {
    return List.copyOf(findings);
  }
}
