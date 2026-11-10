package dev.ward.analysis.frontend.antlr.format.java;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaFormatterOptionsTest {

  private static final String NESTED_SOURCE = "class A {\nvoid f() {\nint x=1;\n}\n}\n";
  private static final String CALL_SOURCE = "class A {\nvoid f() {\nfoo(alpha, beta);\n}\n}\n";

  private final JavaFormatter formatter = new JavaFormatter();

  @Test
  void tabIndentStyleIndentsWithTabs() {
    final FormatResult result = formatter.format(NESTED_SOURCE, options("indent-style", "tab"));

    assertThat(result)
        .isEqualTo(new FormatResult.Formatted("class A {\n\tvoid f() {\n\t\tint x = 1;\n\t}\n}\n"));
  }

  @Test
  void lineWidthOptionBreaksAnArgumentListThatFitsByDefault() {
    final FormatResult defaults = formatter.format(CALL_SOURCE, options());
    final FormatResult narrow = formatter.format(CALL_SOURCE, options("line-width", "20"));

    assertThat(defaults)
        .isEqualTo(
            new FormatResult.Formatted(
                "class A {\n    void f() {\n        foo(alpha, beta);\n    }\n}\n"));
    assertThat(narrow).isInstanceOf(FormatResult.Formatted.class);
    assertThat(((FormatResult.Formatted) narrow).text()).contains("foo(\n");
  }

  private FormatOptions options(final String... pairs) {
    final Map<String, String> values =
        new HashMap<>(FormatOptions.defaults(formatter.options()).values());
    for (int i = 0; i < pairs.length; i += 2) {
      values.put(pairs[i], pairs[i + 1]);
    }
    return new FormatOptions(values);
  }
}
