package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/** Collects the syntax errors of one parse as findings, in the order ANTLR reports them. */
final class SyntaxErrorCollector extends BaseErrorListener {

  /** The rule id of every syntax finding. */
  static final String RULE_ID = "syntax";

  private final List<AnalysisFinding> findings = new ArrayList<>();
  private String path;

  /**
   * Clears the findings and names the unit the next errors belong to.
   *
   * @param unitPath the project-root-relative path of the unit
   */
  void start(final String unitPath) {
    findings.clear();
    path = unitPath;
  }

  /**
   * Returns the findings collected since the last {@link #start}.
   *
   * @return a copy of the findings
   */
  List<AnalysisFinding> findings() {
    return List.copyOf(findings);
  }

  @Override
  public void syntaxError(
      final Recognizer<?, ?> recognizer,
      final Object offendingSymbol,
      final int line,
      final int charPositionInLine,
      final String msg,
      final RecognitionException error) {
    findings.add(
        new AnalysisFinding(
            RULE_ID,
            AnalysisSeverity.ERROR,
            msg,
            path,
            Math.max(1, line),
            Math.max(0, charPositionInLine) + 1,
            null));
  }
}
