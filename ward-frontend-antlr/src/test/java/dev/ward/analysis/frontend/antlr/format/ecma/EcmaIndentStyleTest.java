package dev.ward.analysis.frontend.antlr.format.ecma;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.SourceFormatter;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks that the {@code indent-style} option switches block indentation to tabs. */
class EcmaIndentStyleTest {

  private static final FormatOptions TABS =
      new FormatOptions(
          Map.of(
              "line-width", "100",
              "indent-style", "tab",
              "indent-width", "2",
              "max-blank-lines", "1"));
  private static final String SOURCE = "function f() {\nreturn 1;\n}\n";
  private static final FormatResult TABBED =
      new FormatResult.Formatted("function f() {\n\treturn 1;\n}\n");

  @Test
  void typeScriptIndentsBlocksWithTabs() {
    assertThat(format(new TypeScriptFormatter())).isEqualTo(TABBED);
  }

  @Test
  void javaScriptIndentsBlocksWithTabs() {
    assertThat(format(new JavaScriptFormatter())).isEqualTo(TABBED);
  }

  @Test
  void bothFormattersDeclareIndentStyle() {
    assertThat(new TypeScriptFormatter().options())
        .anyMatch(option -> "indent-style".equals(option.key()));
    assertThat(new JavaScriptFormatter().options())
        .anyMatch(option -> "indent-style".equals(option.key()));
  }

  private static FormatResult format(final SourceFormatter formatter) {
    return formatter.format(SOURCE, TABS);
  }
}
