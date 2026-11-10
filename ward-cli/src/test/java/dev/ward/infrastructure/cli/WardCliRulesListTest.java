package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward rules list}: the filters and the JSON document. */
@DisabledOnOs(OS.WINDOWS)
class WardCliRulesListTest extends WardCliIntegrationSupport {
  private static final String RULES = "rules";
  private static final String LIST = "list";
  private static final String SELECTED = "--selected";
  private static final String PRESET = "--preset";
  private static final String LANGUAGE = "--language";
  private static final String DIRECTORY = "-C";

  @Test
  void jsonListWritesOnlyOneDocumentToStdout() {
    assertThat(
            run(RULES, LIST, PRESET, "standard", "--format", "json", DIRECTORY, project.toString()))
        .isEqualTo(ExitCodes.OK);

    final JsonTree document = JsonTree.parse(out.toString());
    assertThat(document.get("rules").size()).isPositive();
    assertThat(document.get("rules").get(0).get("id").asString()).startsWith("ward:");
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void listFilterByLanguageKeepsOnlyThatLanguage() {
    assertThat(
            run(RULES, LIST, LANGUAGE, "python", "--format", "json", DIRECTORY, project.toString()))
        .isEqualTo(ExitCodes.OK);

    final JsonTree rules = JsonTree.parse(out.toString()).get("rules");
    assertThat(rules.size()).isPositive();
    for (int i = 0; i < rules.size(); i++) {
      final String id = rules.get(i).get("id").asString();
      assertThat(id).matches("ward:(python|architecture)/.+");
    }
  }

  @Test
  void selectedWithoutAFileListsTheStandardRules() {
    assertThat(run(RULES, LIST, SELECTED, "--format", "json", DIRECTORY, project.toString()))
        .isEqualTo(ExitCodes.OK);

    assertThat(JsonTree.parse(out.toString()).get("rules").size()).isPositive();
  }

  @Test
  void anUnknownLanguageIsAUsageError() {
    assertThat(run(RULES, LIST, LANGUAGE, "cobol", DIRECTORY, project.toString()))
        .isEqualTo(ExitCodes.ERROR);

    assertThat(err.toString()).contains("--language must be one of");
  }
}
