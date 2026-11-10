package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import org.junit.jupiter.api.Test;

class DeclarativeChecksTest {

  private static final String LINES_CHECK =
      "check = \"lines\"\nkinds = [\"methodBody\"]\nmax = 60\nmessage = \"{value}\"";

  private static final String DESCENDANT_COUNT_CHECK =
      "check = \"descendant-count\"\nkinds = [\"methodDeclaration\"]\n"
          + "counted = [\"'if'\"]\nmax = 10\nmessage = \"{value}\"";

  private static final String COGNITIVE_CHECK =
      "check = \"cognitive-complexity\"\nkinds = [\"methodDeclaration\"]\n"
          + "structures = [\"'if'\"]\nflat = [\"'&&'\"]\nmax = 15\nmessage = \"{value}\"";

  private static final String TEXT_PATTERN_CHECK =
      "check = \"text-pattern\"\nkinds = [\"methodDeclaration\"]\n"
          + "modifiers = [\"modifier\"]\nname = \"identifier\"\n"
          + "pattern = \"[a-z][a-zA-Z0-9]*\"\nmessage = \"{value}\"";

  private static final String PRECEDING_COMMENT_CHECK =
      "check = \"preceding-comment\"\nkinds = [\"classDeclaration\"]\n"
          + "modifiers = [\"modifier\"]\nrequired = \"public\"\nmessage = \"missing\"";

  @Test
  void buildsEachKnownCheck() {
    assertThat(DeclarativeChecks.bundled().build(LINES_CHECK)).isNotNull();
    assertThat(DeclarativeChecks.bundled().build(DESCENDANT_COUNT_CHECK)).isNotNull();
    assertThat(DeclarativeChecks.bundled().build(COGNITIVE_CHECK)).isNotNull();
    assertThat(DeclarativeChecks.bundled().build(TEXT_PATTERN_CHECK)).isNotNull();
    assertThat(
            DeclarativeChecks.bundled()
                .build("check = \"unreferenced-name\"\nmessage = \"{value}\""))
        .isNotNull();
    assertThat(DeclarativeChecks.bundled().build(PRECEDING_COMMENT_CHECK)).isNotNull();
  }

  @Test
  void unknownCheckNamesTheCheck() {
    assertThatThrownBy(() -> DeclarativeChecks.bundled().build("check = \"nope\"\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("unknown check 'nope'");
  }

  @Test
  void missingCheckKeyIsAConfigurationError() {
    assertThatThrownBy(() -> DeclarativeChecks.bundled().build("message = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'check' must be a string");
  }

  @Test
  void missingLimitIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.bundled()
                    .build("check = \"lines\"\nkinds = [\"methodBody\"]\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'max' must be an integer");
  }

  @Test
  void negativeLimitIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.bundled()
                    .build(
                        "check = \"lines\"\nkinds = [\"methodBody\"]\nmax = -1\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'max' is out of range");
  }

  @Test
  void malformedRegularExpressionIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.bundled()
                    .build(TEXT_PATTERN_CHECK.replace("[a-z][a-zA-Z0-9]*", "[unclosed")))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("check 'text-pattern' cannot be used");
  }

  @Test
  void bodyThatIsNotTomlIsAConfigurationError() {
    assertThatThrownBy(() -> DeclarativeChecks.bundled().build("check = "))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("not valid TOML");
  }

  @Test
  void listedKindsMustBeStrings() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.bundled()
                    .build("check = \"lines\"\nkinds = [1]\nmax = 1\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'kinds' must hold only strings");
  }
}
