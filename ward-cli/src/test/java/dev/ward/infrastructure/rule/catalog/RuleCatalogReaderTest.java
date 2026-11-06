package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.analysis.domain.rule.catalog.CatalogRow;
import dev.ward.analysis.domain.rule.catalog.CatalogStatus;
import dev.ward.analysis.domain.rule.catalog.RuleCatalog;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The catalog format: what a row may say, and what makes a catalog unusable. */
class RuleCatalogReaderTest {

  private static final int MINIMUM_ROWS = 4000;

  private static final String VALID =
      """
      [[rule]]
      name = "method-length"
      category = "sizes"
      presets = ["standard", "strict"]
      status = { java = "planned", kotlin = "implemented", python = "not-applicable" }
      issues = { java = "#284" }
      not-applicable = { python = "no methods" }
      ids = { kotlin = "ward:kotlin/function-length" }
      checkstyle = ["MethodLength"]
      eslint = ["max-lines-per-function"]
      """;

  @Test
  void aRowGivesItsStatusIdsPresetsAndCounterparts() throws IOException {
    final CatalogRow row = parse(VALID).getFirst();

    assertThat(row.name()).isEqualTo("method-length");
    assertThat(row.category()).isEqualTo("sizes");
    assertThat(row.presets()).containsExactlyInAnyOrder(RulePreset.STANDARD, RulePreset.STRICT);
    assertThat(row.status(AnalysisLanguage.JAVA).issue()).isEqualTo("#284");
    assertThat(row.status(AnalysisLanguage.PYTHON).reason()).isEqualTo("no methods");
    assertThat(row.languages(CatalogStatus.IMPLEMENTED)).containsExactly(AnalysisLanguage.KOTLIN);
    assertThat(row.idFor(AnalysisLanguage.KOTLIN)).isEqualTo("ward:kotlin/function-length");
    assertThat(row.idFor(AnalysisLanguage.JAVA)).isEqualTo("ward:java/method-length");
    assertThat(row.counterparts()).containsEntry("checkstyle", List.of("MethodLength"));
    assertThat(row.source()).isEqualTo("test.toml");
  }

  @Test
  void aLanguageThatARowOmitsDoesNotApply() throws IOException {
    final CatalogRow row = parse(VALID).getFirst();

    assertThat(row.status(AnalysisLanguage.SWIFT).status()).isEqualTo(CatalogStatus.NOT_APPLICABLE);
    assertThat(row.status(AnalysisLanguage.SWIFT).reason()).isEqualTo(CatalogRow.UNLISTED_REASON);
  }

  @Test
  void theShippedCatalogLoadsAndFindsRowsByName() {
    final RuleCatalog catalog = new RuleCatalogReader().load();

    assertThat(catalog.rows()).hasSizeGreaterThan(MINIMUM_ROWS);
    assertThat(catalog.row("method-length")).isPresent();
    assertThat(catalog.row("no-such-rule")).isEmpty();
  }

  private static List<CatalogRow> parse(final String text) throws IOException {
    return new RuleCatalogReader().parse("test.toml", new StringReader(text));
  }
}
