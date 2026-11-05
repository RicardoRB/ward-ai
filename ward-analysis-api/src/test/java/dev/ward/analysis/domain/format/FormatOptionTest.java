package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatOptionTest {

  private static final int DEFAULT_INDENT = 4;

  private static final FormatOption INDENT =
      new FormatOption("indent-width", FormatOptionType.INTEGER, "4", List.of());
  private static final FormatOption STYLE =
      new FormatOption("indent-style", FormatOptionType.TEXT, "space", List.of("space", "tab"));

  @Test
  void acceptsValuesOfItsType() {
    assertThat(INDENT.accepts("2")).isTrue();
    assertThat(INDENT.accepts("two")).isFalse();
  }

  @Test
  void restrictsTextToAllowedValues() {
    assertThat(STYLE.accepts("tab")).isTrue();
    assertThat(STYLE.accepts("mixed")).isFalse();
  }

  @Test
  void acceptsOnlyBooleanWords() {
    final FormatOption flag =
        new FormatOption("final-newline", FormatOptionType.BOOLEAN, "true", List.of());

    assertThat(flag.accepts("false")).isTrue();
    assertThat(flag.accepts("yes")).isFalse();
  }

  @Test
  void rejectsDefaultThatItDoesNotAccept() {
    assertThatThrownBy(
            () ->
                new FormatOption(
                    "indent-style", FormatOptionType.TEXT, "mixed", List.of("space", "tab")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void takesDefaultsFromDeclaration() {
    final FormatOptions options = FormatOptions.defaults(List.of(INDENT, STYLE));

    assertThat(options.integer("indent-width")).isEqualTo(DEFAULT_INDENT);
    assertThat(options.get("indent-style")).isEqualTo("space");
  }

  @Test
  void rejectsUnknownOption() {
    final FormatOptions options = new FormatOptions(Map.of());

    assertThatThrownBy(() -> options.get("indent-width"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
