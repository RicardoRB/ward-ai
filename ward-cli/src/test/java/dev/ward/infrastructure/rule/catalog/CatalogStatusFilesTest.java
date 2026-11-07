package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

class CatalogStatusFilesTest {

  private static final CatalogStatusFiles.Entry IMPLEMENTED =
      new CatalogStatusFiles.Entry("implemented", "");
  private static final CatalogStatusFiles.Entry PLANNED =
      new CatalogStatusFiles.Entry("planned", "#12");

  private static TomlTable row(final String text) {
    return Toml.parse(text);
  }

  @Test
  void statusFilesGiveTheStatusOfEachLanguage() {
    final Map<AnalysisLanguage, LanguageStatus> statuses =
        CatalogTables.statuses(
            row("not-applicable = { swift = \"no\" }"),
            Map.of(
                AnalysisLanguage.JAVA, IMPLEMENTED,
                AnalysisLanguage.PYTHON, PLANNED,
                AnalysisLanguage.SWIFT, new CatalogStatusFiles.Entry("not-applicable", "")));

    assertThat(statuses.get(AnalysisLanguage.JAVA).status()).isEqualTo(CatalogStatus.IMPLEMENTED);
    assertThat(statuses.get(AnalysisLanguage.PYTHON).issue()).isEqualTo("#12");
    assertThat(statuses.get(AnalysisLanguage.SWIFT).reason()).isEqualTo("no");
  }

  @Test
  void aLanguageInTheRowAndInAStatusFileIsRejected() {
    assertThatThrownBy(
            () ->
                CatalogTables.statuses(
                    row("status = { java = \"implemented\" }"),
                    Map.of(AnalysisLanguage.JAVA, IMPLEMENTED)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("java");
  }

  @Test
  void aReasonForALanguageWithoutStatusIsRejected() {
    assertThatThrownBy(
            () ->
                CatalogTables.statuses(
                    row("not-applicable = { swift = \"no\" }"),
                    Map.of(AnalysisLanguage.JAVA, IMPLEMENTED)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aMissingStatusFileSaysNothing() {
    assertThat(CatalogStatusFiles.read("no-such-catalog-file")).isEmpty();
  }

  @Test
  void theStatusFilesOfACatalogFileAreRead() {
    assertThat(CatalogStatusFiles.read("checkstyle-sizes").get("file-length"))
        .containsEntry(AnalysisLanguage.JAVA, IMPLEMENTED);
  }
}
