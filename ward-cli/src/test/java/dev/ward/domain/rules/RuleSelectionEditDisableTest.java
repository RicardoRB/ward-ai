package dev.ward.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** Disabling rules: the edit adds to {@code exclude} and removes from {@code include}. */
class RuleSelectionEditDisableTest {
  private static final String METHOD = "ward:java/method-length";
  private static final String FIELD = "java.no-field-di";
  private static final String METHOD_NAME = "java.method-length";

  @Test
  void disableMovesAnIdFromIncludeToExclude() {
    final String text =
        RuleSelectionEdit.disable(
            "[select]\ninclude = [\"" + FIELD + "\", \"" + METHOD_NAME + "\"]\n", List.of(METHOD));

    assertThat(text)
        .isEqualTo(
            "[select]\nexclude = [\"" + METHOD_NAME + "\"]\ninclude = [\"" + FIELD + "\"]\n");
    assertValid(text);
  }

  @Test
  void disableOnAMissingFileWritesAnExcludeEntry() {
    final String text = RuleSelectionEdit.disable(null, List.of(METHOD));

    assertThat(text).isEqualTo("version = 1\n\n[select]\nexclude = [\"" + METHOD_NAME + "\"]\n");
    assertValid(text);
  }

  @Test
  void disableRemovesTheLastElementOfAMultiLineArray() {
    final String text =
        RuleSelectionEdit.disable(
            "[select]\ninclude = [\n    \"" + METHOD_NAME + "\",\n    \"" + FIELD + "\"\n]\n",
            List.of(FIELD));

    assertThat(text)
        .isEqualTo(
            "[select]\nexclude = [\""
                + FIELD
                + "\"]\ninclude = [\n    \""
                + METHOD_NAME
                + "\",\n]\n");
    assertValid(text);
  }

  private static void assertValid(final String text) {
    assertThat(Toml.parse(text).hasErrors()).as(text).isFalse();
  }
}
