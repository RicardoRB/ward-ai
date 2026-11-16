package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatter;
import java.util.List;

/** Runs the Swift formatter over the files of the Swift corpus for {@link SwiftCorpusSmokeTest}. */
final class SwiftCorpusFormat {
  private SwiftCorpusFormat() {}

  /**
   * Formats every file through the safety checks and parses the output with the analysis frontend.
   * Returns the report line, and adds every refused or broken file to {@code problems}.
   */
  static String check(final List<SourceUnit> units, final List<String> problems) {
    final SwiftFormatter formatter = new SwiftFormatter();
    final FormatSafety safety = new FormatSafety(new SwiftFormatSyntax());
    final FormatOptions options = FormatOptions.defaults(formatter.options());
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    int changed = 0;
    for (final SourceUnit unit : units) {
      final FormatResult outcome =
          safety.formatSafely(formatter, options, unit.text().replace("\r\n", "\n"));
      if (outcome instanceof FormatResult.Refused refused) {
        problems.add("%s:%d: refused: %s".formatted(unit.path(), refused.line(), refused.reason()));
      } else if (outcome instanceof FormatResult.Formatted formatted) {
        changed++;
        if (!frontend.parse(written(unit, formatted)).diagnostics().isEmpty()) {
          problems.add(unit.path() + ": the formatted file has syntax errors");
        }
      }
    }
    return "ward format | files %d | need formatting %d | refused or broken %d%n"
        .formatted(units.size(), changed, problems.size());
  }

  private static SourceUnit written(final SourceUnit unit, final FormatResult.Formatted formatted) {
    return new SourceUnit(unit.path(), unit.language(), formatted.text());
  }
}
