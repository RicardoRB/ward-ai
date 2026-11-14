package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The validation of the catalog model, independent of the file format. */
class CatalogModelTest {

  private static final int TOOL_COUNT = 23;

  @Test
  void statusesCarryTheirIssueOrReason() {
    assertThat(LanguageStatus.planned("#12").issue()).isEqualTo("#12");
    assertThat(LanguageStatus.notApplicable("why").reason()).isEqualTo("why");
    assertThat(LanguageStatus.implemented().status()).isEqualTo(CatalogStatus.IMPLEMENTED);
  }

  @Test
  void aStatusWithTheWrongIssueOrReasonIsRejected() {
    assertThatThrownBy(() -> LanguageStatus.planned("12"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LanguageStatus(CatalogStatus.IMPLEMENTED, "#1", ""))
        .hasMessageContaining("only a planned rule");
    assertThatThrownBy(() -> new LanguageStatus(CatalogStatus.PLANNED, "#1", "why"))
        .hasMessageContaining("only a not-applicable rule");
    assertThatThrownBy(() -> LanguageStatus.notApplicable(" "))
        .hasMessageContaining("must give a reason");
  }

  @Test
  void statusKeysRoundTrip() {
    for (final CatalogStatus status : CatalogStatus.values()) {
      assertThat(CatalogStatus.byKey(status.key())).contains(status);
    }
    assertThat(CatalogStatus.byKey("done")).isEmpty();
    assertThat(CatalogStatus.NOT_APPLICABLE.key()).isEqualTo("not-applicable");
  }

  @Test
  void aRowFindsItsIdsAndLanguages() {
    final CatalogRow row =
        new CatalogRow(
            "parameter-number",
            "sizes",
            Set.of(),
            Map.of(
                AnalysisLanguage.JAVA, LanguageStatus.implemented(),
                AnalysisLanguage.KOTLIN, LanguageStatus.planned("#294")),
            Map.of(AnalysisLanguage.JAVA, "ward:java/parameter-count"),
            Map.of("checkstyle", List.of("ParameterNumber")),
            "catalog/x.toml",
            Optional.empty(),
            List.of(),
            List.of());

    assertThat(row.idFor(AnalysisLanguage.JAVA)).isEqualTo("ward:java/parameter-count");
    assertThat(row.idFor(AnalysisLanguage.KOTLIN)).isEqualTo("ward:kotlin/parameter-number");
    assertThat(row.languages(CatalogStatus.PLANNED)).containsExactly(AnalysisLanguage.KOTLIN);
    assertThat(row.languages(CatalogStatus.NOT_APPLICABLE))
        .containsExactly(
            AnalysisLanguage.TYPESCRIPT,
            AnalysisLanguage.JAVASCRIPT,
            AnalysisLanguage.PYTHON,
            AnalysisLanguage.SWIFT,
            AnalysisLanguage.OPENAPI,
            AnalysisLanguage.ANDROID);
  }

  @Test
  void aCounterpartOfAnUnknownToolIsRejected() {
    assertThatThrownBy(
            () ->
                new CatalogRow(
                    "x",
                    "",
                    Set.of(),
                    Map.of(),
                    Map.of(),
                    Map.of("sonar", List.of("S1")),
                    "f",
                    Optional.empty(),
                    List.of(),
                    List.of()))
        .hasMessageContaining("'sonar' is not a tool of the catalog");
  }

  @Test
  void everyToolOfTheRoadmapsIsListedOnce() {
    assertThat(CatalogTools.ALL).hasSize(TOOL_COUNT).doesNotHaveDuplicates();
  }
}
