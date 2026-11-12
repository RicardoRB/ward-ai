package dev.ward.analysis.application.openapi;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.openapi.YamlDiagnostic;
import dev.ward.analysis.domain.openapi.YamlParser;
import dev.ward.analysis.domain.openapi.YamlResult;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.List;

/**
 * The frontend of OpenAPI units: it reads YAML and JSON alike with {@link YamlParser}, and reports
 * a syntax problem as a diagnostic next to an empty tree. The text, not the extension, decides.
 */
public final class OpenApiLanguageFrontend implements LanguageFrontend {

  private static final String RULE_ID = "syntax";

  @Override
  public ParseResult parse(final SourceUnit unit) {
    final YamlResult result = YamlParser.parse(unit.text());
    final List<AnalysisFinding> diagnostics =
        result.diagnostics().stream().map(problem -> finding(unit, problem)).toList();
    return new ParseResult(result.tree(), diagnostics);
  }

  private static AnalysisFinding finding(final SourceUnit unit, final YamlDiagnostic problem) {
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
