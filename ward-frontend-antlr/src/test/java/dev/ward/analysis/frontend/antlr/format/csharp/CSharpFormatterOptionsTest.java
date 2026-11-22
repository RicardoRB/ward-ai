package dev.ward.analysis.frontend.antlr.format.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks that the formatter reads its options from {@link FormatOptions}. */
class CSharpFormatterOptionsTest {
  private static final CSharpFormatter FORMATTER = new CSharpFormatter();

  @Test
  void tabIndentStyleIndentsWithTabs() {
    final String out =
        format("class A\n{\n    int x;\n}\n", Map.of(CSharpFormatter.INDENT_STYLE, "tab"));

    assertThat(out).isEqualTo("class A\n{\n\tint x;\n}\n");
  }

  @Test
  void indentWidthSetsTheColumnsOfOneLevel() {
    final String out =
        format("class A\n{\n    int x;\n}\n", Map.of(CSharpFormatter.INDENT_WIDTH, "2"));

    assertThat(out).isEqualTo("class A\n{\n  int x;\n}\n");
  }

  @Test
  void crlfInputIsWrittenWithLineFeeds() {
    final String out = format("int a;\r\nint b;\r\n", Map.of());

    assertThat(out).isEqualTo("int a;\nint b;\n");
  }

  private static String format(final String source, final Map<String, String> overrides) {
    final FormatOptions defaults = FormatOptions.defaults(FORMATTER.options());
    final Map<String, String> values = new HashMap<>(defaults.values());
    values.putAll(overrides);
    final FormatResult result = FORMATTER.format(source, new FormatOptions(values));
    if (result instanceof FormatResult.Formatted formatted) {
      return formatted.text();
    }
    if (result instanceof FormatResult.Unchanged) {
      return source;
    }
    throw new AssertionError("refused: " + result);
  }
}
