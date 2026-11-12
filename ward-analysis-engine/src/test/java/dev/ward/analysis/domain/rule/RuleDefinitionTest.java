package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleDefinitionTest {

  @Test
  void acceptsIdNamingItsLanguage() {
    assertThatCode(() -> definition("ward:java/method-length", AnalysisLanguage.JAVA))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsMultiWordLanguageName() {
    assertThatCode(() -> definition("ward:typescript/no-any", AnalysisLanguage.TYPESCRIPT))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "method-length",
        "ward:java/",
        "ward:java/Method-Length",
        "ward:python/method-length",
        "ward:java/method length"
      })
  void rejectsMalformedOrForeignId(final String id) {
    assertThatThrownBy(() -> definition(id, AnalysisLanguage.JAVA))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ward:java/<rule>");
  }

  @Test
  void rejectsMissingCapability() {
    assertThatThrownBy(
            () ->
                new RuleDefinition(
                    "ward:java/method-length",
                    AnalysisLanguage.JAVA,
                    null,
                    AnalysisSeverity.WARNING,
                    ""))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void acceptsTextRuleWithNoLanguageAndSecretsPrefix() {
    assertThatCode(() -> text("ward:secrets/github-pat", Set.of())).doesNotThrowAnyException();
  }

  @Test
  void rejectsTextRuleWithALanguage() {
    assertThatThrownBy(() -> text("ward:secrets/github-pat", Set.of(AnalysisLanguage.JAVA)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no language");
  }

  @Test
  void rejectsLanguageRuleWithNoLanguage() {
    assertThatThrownBy(
            () ->
                new RuleDefinition(
                    "ward:java/method-length",
                    Set.of(),
                    RuleCapability.SYNTAX,
                    AnalysisSeverity.WARNING,
                    "body"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one language");
  }

  @Test
  void rejectsTextRuleWithAnotherPrefix() {
    assertThatThrownBy(() -> text("ward:java/github-pat", Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ward:secrets/<rule>");
  }

  private static RuleDefinition text(final String id, final Set<AnalysisLanguage> languages) {
    return new RuleDefinition(id, languages, RuleCapability.TEXT, AnalysisSeverity.ERROR, "body");
  }

  private static RuleDefinition definition(final String id, final AnalysisLanguage language) {
    return new RuleDefinition(
        id, language, RuleCapability.SYNTAX, AnalysisSeverity.WARNING, "body");
  }
}
