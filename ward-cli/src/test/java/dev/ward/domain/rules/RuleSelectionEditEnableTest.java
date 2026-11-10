package dev.ward.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** Enabling rules: the edit adds to {@code include} and keeps the rest of the file. */
class RuleSelectionEditEnableTest {
  private static final String METHOD = "ward:java/method-length";
  private static final String FIELD = "java.no-field-di";
  private static final String METHOD_NAME = "java.method-length";

  @Test
  void enableOnAMissingFileWritesVersionAndSelect() {
    final String text = RuleSelectionEdit.enable(null, List.of(METHOD));

    assertThat(text).isEqualTo("version = 1\n\n[select]\ninclude = [\"" + METHOD_NAME + "\"]\n");
    assertValid(text);
  }

  @Test
  void enableAddsTheSelectTableWhenTheFileHasNone() {
    final String text = RuleSelectionEdit.enable("version = 1\n", List.of(METHOD));

    assertThat(text).isEqualTo("version = 1\n\n[select]\ninclude = [\"" + METHOD_NAME + "\"]\n");
    assertValid(text);
  }

  @Test
  void enableAppendsToAnExistingInlineArray() {
    final String text =
        RuleSelectionEdit.enable(
            "version = 1\n[select]\ninclude = [\"" + FIELD + "\"]\n", List.of(METHOD));

    assertThat(text)
        .isEqualTo(
            "version = 1\n[select]\ninclude = [\"" + FIELD + "\", \"" + METHOD_NAME + "\"]\n");
    assertValid(text);
  }

  @Test
  void enableFillsAnEmptyArray() {
    final String text = RuleSelectionEdit.enable("[select]\ninclude = []\n", List.of(METHOD));

    assertThat(text).isEqualTo("[select]\ninclude = [\"" + METHOD_NAME + "\"]\n");
    assertValid(text);
  }

  @Test
  void enableKeepsCommentsAndOtherLinesOfAFile() {
    final String original =
        "# Ward rules for this project.\n"
            + "version = 1\n"
            + "\n"
            + "[select]\n"
            + "preset = \"standard\"  # the default set\n"
            + "# rules we want on top of it\n"
            + "include = [\n"
            + "    \"java.no-field-di\", # DI rule\n"
            + "]\n"
            + "\n"
            + "[rules.java.method-length]\n"
            + "max = 80\n";

    final String text = RuleSelectionEdit.enable(original, List.of(METHOD));

    assertThat(text)
        .isEqualTo(
            "# Ward rules for this project.\n"
                + "version = 1\n"
                + "\n"
                + "[select]\n"
                + "preset = \"standard\"  # the default set\n"
                + "# rules we want on top of it\n"
                + "include = [\n"
                + "    \"java.no-field-di\", # DI rule\n"
                + "    \"java.method-length\",\n"
                + "]\n"
                + "\n"
                + "[rules.java.method-length]\n"
                + "max = 80\n");
    assertValid(text);
  }

  @Test
  void enableMovesAnIdOutOfExclude() {
    final String text =
        RuleSelectionEdit.enable(
            "[select]\nexclude = [\"" + METHOD_NAME + "\"]\n", List.of(METHOD));

    assertThat(text).isEqualTo("[select]\ninclude = [\"" + METHOD_NAME + "\"]\nexclude = []\n");
    assertValid(text);
  }

  @Test
  void enableDoesNotDuplicateAnId() {
    final String original = "[select]\ninclude = [\"" + METHOD_NAME + "\"]\n";

    assertThat(RuleSelectionEdit.enable(original, List.of(METHOD))).isEqualTo(original);
  }

  @Test
  void anUnterminatedArrayIsRefused() {
    assertThatThrownBy(() -> RuleSelectionEdit.enable("[select]\ninclude = [\n", List.of(METHOD)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aKeyThatIsNotAnArrayIsRefused() {
    assertThatThrownBy(
            () -> RuleSelectionEdit.enable("[select]\ninclude = \"x\"\n", List.of(METHOD)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("include");
  }

  private static void assertValid(final String text) {
    assertThat(Toml.parse(text).hasErrors()).as(text).isFalse();
  }
}
