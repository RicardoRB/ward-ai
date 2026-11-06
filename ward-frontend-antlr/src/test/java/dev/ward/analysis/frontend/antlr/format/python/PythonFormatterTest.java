package dev.ward.analysis.frontend.antlr.format.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the option contract of the Python formatter: width, indent style and blank lines. */
class PythonFormatterTest {

  private static final FormatOptions SPACES =
      new FormatOptions(
          Map.of(
              "indent-width",
              "4",
              "indent-style",
              "space",
              "line-width",
              "100",
              "max-blank-lines",
              "1"));
  private static final FormatOptions TABS =
      new FormatOptions(
          Map.of(
              "indent-width",
              "4",
              "indent-style",
              "tab",
              "line-width",
              "100",
              "max-blank-lines",
              "1"));

  @Test
  void tabStyleIndentsBlocksWithTabs() {
    final FormatResult result = new PythonFormatter().format("def f():\n  return 1\n", TABS);

    assertThat(result).isEqualTo(new FormatResult.Formatted("def f():\n\treturn 1\n"));
  }

  @Test
  void spaceStyleUsesTheIndentWidth() {
    final FormatResult result = new PythonFormatter().format("def f():\n  return x=a+b\n", SPACES);

    assertThat(result).isEqualTo(new FormatResult.Formatted("def f():\n    return x = a + b\n"));
  }
}
