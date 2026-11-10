package dev.ward.analysis.frontend.antlr.format.ecma;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.SourceFormatter;
import org.junit.jupiter.api.Test;

/** Checks that the safety checks refuse a layout that changes how a statement parses. */
class EcmaFormatSafetyTest {

  private static final String RETURN_SOURCE = "function f() {\n  return 1;\n}\n";
  private static final String BROKEN_RETURN = "function f() {\n  return\n  1;\n}\n";

  @Test
  void typeScriptRefusesALineBreakAfterReturn() {
    assertRefusesBreak(new TypeScriptFormatSyntax(), new TypeScriptFormatter(), RETURN_SOURCE);
  }

  @Test
  void javaScriptRefusesALineBreakAfterReturn() {
    assertRefusesBreak(new JavaScriptFormatSyntax(), new JavaScriptFormatter(), RETURN_SOURCE);
  }

  @Test
  void formatterKeepsReturnAndItsExpressionOnOneLine() {
    final String source =
        "function f() {\n  return someFunctionName(argumentOneValueWithLongName, argumentTwoValueLong,"
            + " argumentThreeValueLonger, argumentFourValue);\n}\n";
    final FormatResult result =
        new TypeScriptFormatter()
            .format(source, FormatOptions.defaults(new TypeScriptFormatter().options()));

    assertThat(result).isInstanceOf(FormatResult.Formatted.class);
    assertThat(((FormatResult.Formatted) result).text())
        .contains("  return someFunctionName(\n    argumentOneValueWithLongName,");
  }

  private static void assertRefusesBreak(
      final FormatSyntax syntax, final SourceFormatter formatter, final String source) {
    final SourceFormatter broken = new FixedFormatter(formatter, BROKEN_RETURN);
    final FormatResult result =
        new FormatSafety(syntax)
            .formatSafely(broken, FormatOptions.defaults(formatter.options()), source);

    assertThat(result).isInstanceOf(FormatResult.Refused.class);
    assertThat(((FormatResult.Refused) result).reason())
        .isEqualTo("formatting changed the syntax tree");
  }
}
