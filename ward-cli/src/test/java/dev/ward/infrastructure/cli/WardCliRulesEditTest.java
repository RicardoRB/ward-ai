package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward rules enable and disable}: the rules file they write. */
@DisabledOnOs(OS.WINDOWS)
class WardCliRulesEditTest extends WardCliIntegrationSupport {
  private static final String RULES = "rules";
  private static final String ENABLE = "enable";
  private static final String DISABLE = "disable";
  private static final String DIRECTORY = "-C";
  private static final String METHOD = "ward:java/method-length";
  private static final String FIELD = "java.no-field-di";
  private static final String METHOD_NAME = "java.method-length";
  private static final String FILE = "ward-rules.toml";

  @BeforeEach
  void noRulesFileYet() throws IOException {
    Files.deleteIfExists(project.resolve(FILE));
  }

  @Test
  void enableCreatesTheFileWhenItIsMissing() throws IOException {
    assertThat(run(RULES, ENABLE, METHOD, DIRECTORY, project.toString())).isEqualTo(ExitCodes.OK);

    final String text = Files.readString(project.resolve(FILE));
    assertThat(text).contains("version = 1").contains("include = [\"" + METHOD_NAME + "\"]");
  }

  @Test
  void enableKeepsCommentsOfAnExistingFile() throws IOException {
    Files.writeString(
        project.resolve(FILE),
        "# team rules\nversion = 1\n\n[select]\n# turn on the DI rule\ninclude = [\""
            + FIELD
            + "\"]\n");

    assertThat(run(RULES, ENABLE, METHOD, DIRECTORY, project.toString())).isEqualTo(ExitCodes.OK);

    final String text = Files.readString(project.resolve(FILE));
    assertThat(text)
        .contains("# team rules")
        .contains("# turn on the DI rule")
        .contains("\"" + FIELD + "\", \"" + METHOD_NAME + "\"");
  }

  @Test
  void disableMovesTheRuleToExclude() throws IOException {
    Files.writeString(
        project.resolve(FILE), "version = 1\n[select]\ninclude = [\"" + METHOD_NAME + "\"]\n");

    assertThat(run(RULES, DISABLE, METHOD, DIRECTORY, project.toString())).isEqualTo(ExitCodes.OK);

    final String text = Files.readString(project.resolve(FILE));
    assertThat(text)
        .contains("exclude = [\"" + METHOD_NAME + "\"]")
        .doesNotContain("include = [\"" + METHOD_NAME);
  }

  @Test
  void anUnknownIdWritesNothing() {
    assertThat(run(RULES, ENABLE, "ward:java/nope", DIRECTORY, project.toString()))
        .isEqualTo(ExitCodes.ERROR);

    assertThat(project.resolve(FILE)).doesNotExist();
  }
}
