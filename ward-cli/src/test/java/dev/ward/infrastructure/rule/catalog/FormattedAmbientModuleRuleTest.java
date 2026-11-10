package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.format.ecma.TypeScriptFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The formatter's output for a multi-statement ambient module passes one-statement-per-line. */
class FormattedAmbientModuleRuleTest {

  private static final String RULE_ID = "ward:typescript/one-statement-per-line";
  private static final String SHIM =
      """
      declare module "*.vue" {
        import type { DefineComponent } from "vue";
        const component: DefineComponent<object, object, unknown>;
        export default component;
      }
      """;

  @Test
  void formattedVueShimHasNoOneStatementPerLineFinding() {
    final String formatted = format(SHIM);

    assertThat(oneStatementFindings(formatted)).isEmpty();
  }

  @Test
  void joinedVueShimIsReportedSoTheCheckIsMeaningful() {
    final String joined =
        "declare module \"*.vue\" { const component: unknown; export default component; }\n";

    assertThat(oneStatementFindings(joined)).isNotEmpty();
  }

  /** The formatted text, or the input itself when the formatter leaves it unchanged. */
  private static String format(final String source) {
    final SourceFormatter formatter = new TypeScriptFormatter();
    final FormatResult result =
        formatter.format(source, FormatOptions.defaults(formatter.options()));
    return switch (result) {
      case FormatResult.Formatted formatted -> formatted.text();
      case FormatResult.Unchanged unchanged -> source;
      case FormatResult.Refused refused -> throw new AssertionError(refused.reason());
    };
  }

  private static List<AnalysisFinding> oneStatementFindings(final String text) {
    final RuleDefinition rule =
        CatalogRuleDefinitions.shipped().stream()
            .filter(candidate -> RULE_ID.equals(candidate.id()))
            .findFirst()
            .orElseThrow();
    return new RuleFixtureRunner()
        .run(rule, "shims-vue.d.ts", text, FixtureTypeFacts.of(Optional.empty()));
  }
}
