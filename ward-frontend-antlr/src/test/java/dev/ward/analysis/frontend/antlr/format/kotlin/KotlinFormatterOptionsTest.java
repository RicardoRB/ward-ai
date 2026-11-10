package dev.ward.analysis.frontend.antlr.format.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks that the formatter reads its options from {@link FormatOptions}. */
class KotlinFormatterOptionsTest {

  private static final KotlinFormatter FORMATTER = new KotlinFormatter();

  @Test
  void tabIndentStyleIndentsWithTabs() {
    final String out =
        format("fun f() {\n    val x = 1\n}\n", Map.of(KotlinFormatter.INDENT_STYLE, "tab"));

    assertThat(out).isEqualTo("fun f() {\n\tval x = 1\n}\n");
  }

  @Test
  void narrowLineWidthBreaksAnArgumentListOneItemPerLine() {
    final String out =
        format("val r = call(alpha, beta)\n", Map.of(KotlinFormatter.LINE_WIDTH, "20"));

    assertThat(out).isEqualTo("val r = call(\n        alpha,\n        beta\n)\n");
  }

  @Test
  void crlfInputIsWrittenWithLineFeeds() {
    final String out = format("val a = 1\r\nval b = 2\r\n", Map.of());

    assertThat(out).isEqualTo("val a = 1\nval b = 2\n");
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
