package dev.ward.analysis.frontend.antlr.format.java;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaTypeListFormatTest {
  private static final String TOO_LONG =
      "public interface EnrollmentRepository\n"
          + "        extends java.util.function.Function<java.util.List<String>, "
          + "java.util.Map<String, Integer>>,\n"
          + "        java.util.function.Supplier<String> {}\n";
  private static final String BROKEN =
      "public interface EnrollmentRepository\n"
          + "        extends java.util.function.Function<java.util.List<String>, "
          + "java.util.Map<String, Integer>>,\n"
          + "                java.util.function.Supplier<String> {}\n";
  private static final String FITS =
      "interface X extends JpaRepository<E, UUID>, XCustom {}\n";

  private final JavaFormatter formatter = new JavaFormatter();

  @Test
  @DisplayName("An extends list that does not fit breaks after the comma and stays stable")
  void longExtendsListBreaksAfterTheComma() {
    final String once = format(TOO_LONG, "100");

    assertThat(once).isEqualTo(BROKEN);
    assertThat(format(once, "100")).isEqualTo(once);
  }

  @Test
  @DisplayName("An extends list that fits stays on one line even when the source broke it")
  void shortExtendsListStaysOnOneLine() {
    assertThat(format(FITS, "100")).isEqualTo(FITS);
    assertThat(format("interface X extends JpaRepository<E, UUID>,\n    XCustom {}\n", "100"))
        .isEqualTo(FITS);
  }

  private String format(final String source, final String width) {
    final Map<String, String> values =
        new HashMap<>(FormatOptions.defaults(formatter.options()).values());
    values.put("line-width", width);
    final FormatResult result = formatter.format(source, new FormatOptions(values));
    return result instanceof FormatResult.Formatted formatted ? formatted.text() : source;
  }
}
