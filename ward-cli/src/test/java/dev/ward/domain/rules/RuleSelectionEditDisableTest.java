package dev.ward.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** Disabling rules: the edit adds to {@code exclude} and removes from {@code include}. */
class RuleSelectionEditDisableTest {
  private static final String METHOD = "ward:java/method-length";
  private static final String FIELD = "ward:java/no-field-di";

  @Test
  void disableMovesAnIdFromIncludeToExclude() {
    final String text =
        RuleSelectionEdit.disable(
            "[select]\ninclude = [\"" + FIELD + "\", \"" + METHOD + "\"]\n", List.of(METHOD));

    assertThat(text)
        .isEqualTo("[select]\nexclude = [\"" + METHOD + "\"]\ninclude = [\"" + FIELD + "\"]\n");
    assertValid(text);
  }

  @Test
  void disableOnAMissingFileWritesAnExcludeEntry() {
    final String text = RuleSelectionEdit.disable(null, List.of(METHOD));

    assertThat(text).isEqualTo("version = 1\n\n[select]\nexclude = [\"" + METHOD + "\"]\n");
    assertValid(text);
  }

  @Test
  void disableRemovesTheLastElementOfAMultiLineArray() {
    final String text =
        RuleSelectionEdit.disable(
            "[select]\ninclude = [\n    \"" + METHOD + "\",\n    \"" + FIELD + "\"\n]\n",
            List.of(FIELD));

    assertThat(text)
        .isEqualTo(
            "[select]\nexclude = [\"" + FIELD + "\"]\ninclude = [\n    \"" + METHOD + "\",\n]\n");
    assertValid(text);
  }

  private static void assertValid(final String text) {
    assertThat(Toml.parse(text).hasErrors()).as(text).isFalse();
  }
}
