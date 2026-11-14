package dev.ward.analysis.application.android;

import dev.ward.analysis.domain.android.XmlDiagnostic;
import dev.ward.analysis.domain.android.XmlParser;
import dev.ward.analysis.domain.android.XmlResult;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.List;

/**
 * The frontend of Android units: it reads the manifest and resource XML with {@link XmlParser} and
 * reports a syntax problem as a diagnostic next to an empty tree.
 */
public final class AndroidXmlFrontend implements LanguageFrontend {

  private static final String RULE_ID = "syntax";

  @Override
  public ParseResult parse(final SourceUnit unit) {
    final XmlResult result = XmlParser.parse(unit.text());
    final List<AnalysisFinding> diagnostics =
        result.diagnostics().stream().map(problem -> finding(unit, problem)).toList();
    return new ParseResult(result.tree(), diagnostics);
  }

  private static AnalysisFinding finding(final SourceUnit unit, final XmlDiagnostic problem) {
    return new AnalysisFinding(
        RULE_ID,
        AnalysisSeverity.ERROR,
        problem.message(),
        unit.path(),
        problem.line(),
        problem.column(),
        null,
        List.of());
  }
}
