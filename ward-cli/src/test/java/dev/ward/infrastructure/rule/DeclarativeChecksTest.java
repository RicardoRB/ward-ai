package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import org.junit.jupiter.api.Test;

class DeclarativeChecksTest {

  @Test
  void buildsEachKnownCheck() {
    assertThat(DeclarativeChecks.build(lines())).isNotNull();
    assertThat(DeclarativeChecks.build(descendantCount())).isNotNull();
    assertThat(DeclarativeChecks.build(cognitive())).isNotNull();
    assertThat(DeclarativeChecks.build(textPattern())).isNotNull();
    assertThat(DeclarativeChecks.build("check = \"unreferenced-name\"\nmessage = \"{value}\""))
        .isNotNull();
    assertThat(DeclarativeChecks.build(precedingComment())).isNotNull();
  }

  @Test
  void unknownCheckNamesTheCheck() {
    assertThatThrownBy(() -> DeclarativeChecks.build("check = \"nope\"\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("unknown check 'nope'");
  }

  @Test
  void missingCheckKeyIsAConfigurationError() {
    assertThatThrownBy(() -> DeclarativeChecks.build("message = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'check' must be a string");
  }

  @Test
  void missingLimitIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.build(
                    "check = \"lines\"\nkinds = [\"methodBody\"]\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'max' must be an integer");
  }

  @Test
  void negativeLimitIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.build(
                    "check = \"lines\"\nkinds = [\"methodBody\"]\nmax = -1\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'max' is out of range");
  }

  @Test
  void malformedRegularExpressionIsAConfigurationError() {
    assertThatThrownBy(
            () -> DeclarativeChecks.build(textPattern().replace("[a-z][a-zA-Z0-9]*", "[unclosed")))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("check 'text-pattern' cannot be used");
  }

  @Test
  void bodyThatIsNotTomlIsAConfigurationError() {
    assertThatThrownBy(() -> DeclarativeChecks.build("check = "))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("not valid TOML");
  }

  @Test
  void listedKindsMustBeStrings() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.build("check = \"lines\"\nkinds = [1]\nmax = 1\nmessage = \"m\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("'kinds' must hold only strings");
  }

  private static String lines() {
    return "check = \"lines\"\nkinds = [\"methodBody\"]\nmax = 60\nmessage = \"{value}\"";
  }

  private static String descendantCount() {
    return "check = \"descendant-count\"\nkinds = [\"methodDeclaration\"]\n"
        + "counted = [\"'if'\"]\nmax = 10\nmessage = \"{value}\"";
  }

  private static String cognitive() {
    return "check = \"cognitive-complexity\"\nkinds = [\"methodDeclaration\"]\n"
        + "structures = [\"'if'\"]\nflat = [\"'&&'\"]\nmax = 15\nmessage = \"{value}\"";
  }

  private static String textPattern() {
    return "check = \"text-pattern\"\nkinds = [\"methodDeclaration\"]\n"
        + "modifiers = [\"modifier\"]\nname = \"identifier\"\n"
        + "pattern = \"[a-z][a-zA-Z0-9]*\"\nmessage = \"{value}\"";
  }

  private static String precedingComment() {
    return "check = \"preceding-comment\"\nkinds = [\"classDeclaration\"]\n"
        + "modifiers = [\"modifier\"]\nrequired = \"public\"\nmessage = \"missing\"";
  }
}
