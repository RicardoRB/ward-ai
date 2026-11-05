package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FormatSafetyTest {

  private static final FormatOptions NO_OPTIONS = new FormatOptions(Map.of());
  private final FormatSafety safety = new FormatSafety(new StubFormatSyntax());

  static StubFormatter formatter(final Function<String, FormatResult> behavior) {
    return new StubFormatter(AnalysisLanguage.JAVA, behavior);
  }

  private FormatResult run(final String source, final StubFormatter formatter) {
    return safety.formatSafely(formatter, NO_OPTIONS, source);
  }

  @Test
  void acceptsWhitespaceOnlyChange() {
    final StubFormatter formatter =
        formatter(
            s ->
                "a  b".equals(s)
                    ? new FormatResult.Formatted("a b")
                    : new FormatResult.Unchanged());

    assertThat(run("a  b", formatter)).isEqualTo(new FormatResult.Formatted("a b"));
  }

  @Test
  void reportsAlreadyFormattedSourceAsUnchanged() {
    final StubFormatter formatter = formatter(s -> new FormatResult.Unchanged());

    assertThat(run("a b", formatter)).isEqualTo(new FormatResult.Unchanged());
  }

  @Test
  void treatsSameTextAsUnchanged() {
    final StubFormatter formatter = formatter(s -> new FormatResult.Formatted(s));

    assertThat(run("a b", formatter)).isEqualTo(new FormatResult.Unchanged());
  }
}
