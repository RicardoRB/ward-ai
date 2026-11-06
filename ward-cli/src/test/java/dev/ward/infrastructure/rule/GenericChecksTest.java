package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.ModifierTable;
import dev.ward.application.gate.AnalysisRuleLoad;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GenericChecksTest {

  private static final String MESSAGE = "\nmessage = \"m\"";

  /** The smallest valid body of each kind; a kind without an entry fails the test below. */
  private static final Map<GenericKind, String> MINIMAL =
      Map.of(
          GenericKind.IDENTIFIER_PATTERN,
          "check = \"identifier-pattern\"\nkinds = [\"a\"]\nname = \"n\"\npattern = \"x\""
              + MESSAGE,
          GenericKind.FORBIDDEN_NODE,
          "check = \"forbidden-node\"\nkinds = [\"a\"]" + MESSAGE,
          GenericKind.REQUIRED_NODE,
          "check = \"required-node\"\nkinds = [\"a\"]\nchild = [\"b\"]" + MESSAGE,
          GenericKind.COUNT,
          "check = \"count\"\nkinds = [\"a\"]\ncounted = [\"b\"]\nmax = 1" + MESSAGE,
          GenericKind.DEPTH,
          "check = \"depth\"\nkinds = [\"a\"]\nmax = 1" + MESSAGE,
          GenericKind.TEXT_PATTERN,
          "check = \"text-pattern\"\ntarget = \"lines\"\npattern = \"x\"" + MESSAGE,
          GenericKind.ORDER,
          "check = \"order\"\nkinds = [\"a\"]\norder = [\"b\", \"c\"]" + MESSAGE,
          GenericKind.LITERAL,
          "check = \"literal\"\nkinds = [\"a\"]\nignore = [\"0\"]" + MESSAGE,
          GenericKind.DUPLICATE,
          "check = \"duplicate\"\nkinds = [\"a\"]\nitems = [\"b\"]" + MESSAGE);

  @ParameterizedTest
  @EnumSource(GenericKind.class)
  void everyKindBuildsFromItsMinimalBody(final GenericKind kind) {
    assertThat(MINIMAL).containsKey(kind);
    assertThat(DeclarativeChecks.build(MINIMAL.get(kind))).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(GenericKind.class)
  void everyKindRejectsAnUnknownParameter(final GenericKind kind) {
    assertThatThrownBy(() -> DeclarativeChecks.build(MINIMAL.get(kind) + "\nbogus = 1"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("check '" + kind.id() + "' does not accept bogus");
  }

  @Test
  void anUnknownKindNamesTheKind() {
    assertThatThrownBy(() -> DeclarativeChecks.build("check = \"forbidden-thing\"" + MESSAGE))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("unknown check 'forbidden-thing'");
  }

  @Test
  void aRuleRowWithAnUnknownKindFailsLoadingAndNamesTheRule() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("rules-examples/bad-kind.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("ward:java/example-bad-kind")
        .contains("forbidden-thing");
  }

  @Test
  void aBadSourcesValueIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.build(
                    MINIMAL.get(GenericKind.FORBIDDEN_NODE) + "\nsources = \"some\""))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("sources must be all, main or test");
  }

  @Test
  void anUnknownTextTargetIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                DeclarativeChecks.build(
                    MINIMAL.get(GenericKind.TEXT_PATTERN).replace("lines", "words")))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("target must be");
  }

  @Test
  void textPatternWithoutATargetStaysTheNamePatternCheck() {
    final String legacy =
        "check = \"text-pattern\"\nkinds = [\"a\"]\nmodifiers = [\"m\"]\nname = \"n\"\n"
            + "pattern = \"x\"\nmessage = \"m\"";

    assertThat(DeclarativeChecks.build(legacy)).isNotNull();
    assertThat(CheckOptions.of(legacy)).isEmpty();
  }

  @Test
  void eachKindListsItsUserOptionsWithTheBodysValues() {
    assertThat(CheckOptions.of(MINIMAL.get(GenericKind.COUNT)))
        .containsExactly(new CheckOption("max", "integer", "1"));
    assertThat(CheckOptions.of(MINIMAL.get(GenericKind.ORDER)))
        .containsExactly(new CheckOption("order", "string-list", "b,c"));
    assertThat(CheckOptions.of(MINIMAL.get(GenericKind.IDENTIFIER_PATTERN)))
        .containsExactly(new CheckOption("pattern", "string", "x"));
    assertThat(CheckOptions.of(MINIMAL.get(GenericKind.LITERAL)))
        .containsExactly(
            new CheckOption("ignore", "string-list", "0"),
            new CheckOption("ignore-contexts", "string-list", ""),
            new CheckOption("max-repeats", "integer", ""));
    assertThat(CheckOptions.of(MINIMAL.get(GenericKind.DUPLICATE))).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"JAVA", "KOTLIN", "PYTHON", "SWIFT", "TYPESCRIPT", "JAVASCRIPT"})
  void everyEngineLanguageShipsAModifierTable(final AnalysisLanguage language) {
    final ModifierTable table = ModifierTables.forLanguage(language);

    assertThat(table.texts()).containsKeys("static", "final");
  }
}
