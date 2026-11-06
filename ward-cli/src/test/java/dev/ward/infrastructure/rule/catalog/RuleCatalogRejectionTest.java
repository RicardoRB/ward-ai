package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What makes a catalog row or a catalog unusable. */
class RuleCatalogRejectionTest {

  private static final String VALID =
      """
      [[rule]]
      name = "method-length"
      category = "sizes"
      status = { java = "planned" }
      issues = { java = "#284" }
      checkstyle = ["MethodLength"]
      """;

  @Test
  void aPlannedRowWithoutAnIssueFails() {
    assertRejected(
        """
        [[rule]]
        name = "x"
        category = "c"
        status = { java = "planned" }
        """,
        "a planned rule must name its issue");
  }

  @Test
  void aNotApplicableRowWithoutAReasonFails() {
    assertRejected(
        """
        [[rule]]
        name = "x"
        status = { java = "not-applicable" }
        """,
        "a not-applicable rule must give a reason");
  }

  @Test
  void anImplementedRowThatNamesAnIssueFails() {
    assertRejected(
        """
        [[rule]]
        name = "x"
        status = { java = "implemented" }
        issues = { java = "#1" }
        """,
        "only a planned rule names an issue");
  }

  @Test
  void anIssueForALanguageWithoutAStatusFails() {
    assertRejected(
        """
        [[rule]]
        name = "x"
        status = { java = "implemented" }
        issues = { kotlin = "#1" }
        """,
        "'issues' names kotlin with no status");
  }

  @Test
  void anUnknownStatusLanguageToolOrKeyFails() {
    assertRejected(
        "[[rule]]\nname = \"x\"\nstatus = { java = \"done\" }\n", "unknown status for java");
    assertRejected(
        "[[rule]]\nname = \"x\"\nstatus = { cobol = \"planned\" }\n", "not an engine language");
    assertRejected("[[rule]]\nname = \"x\"\nsonar = [\"S1\"]\n", "unknown key 'sonar'");
    assertRejected("[[rule]]\nname = \"x\"\npresets = [\"loose\"]\n", "is not standard or strict");
  }

  @Test
  void aBadNameOrIdFails() {
    assertRejected("[[rule]]\nname = \"Bad_Name\"\n", "must be lower-case words");
    assertRejected(
        "[[rule]]\nname = \"x\"\nids = { java = \"ward:kotlin/x\" }\n", "must be ward:java/<name>");
    assertRejected("[[rule]]\nname = \"\"\n", "'name' must be a non-blank string");
  }

  @Test
  void anInvalidFileOrACounterpartThatIsNotAListFails() {
    assertThatThrownBy(() -> parse("[[rule]\n")).isInstanceOf(RuleConfigurationException.class);
    assertRejected("[[rule]]\nname = \"x\"\neslint = \"max-len\"\n", "must be a list of strings");
  }

  @Test
  void twoRowsWithTheSameNameOrTheSameUpstreamRuleFail() throws IOException {
    final List<CatalogRow> twice = parse(VALID + "\n" + VALID);

    assertThatThrownBy(() -> new RuleCatalog(twice))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("Rule name 'method-length' is in the catalog twice");
    final List<CatalogRow> sameRule =
        parse(VALID + "\n[[rule]]\nname = \"other\"\ncheckstyle = [\"MethodLength\"]\n");
    assertThatThrownBy(() -> new RuleCatalog(sameRule))
        .hasMessageContaining("The checkstyle rule 'MethodLength' is in the catalog twice");
  }

  private static List<CatalogRow> parse(final String text) throws IOException {
    return new RuleCatalogReader().parse("test.toml", new StringReader(text));
  }

  private static void assertRejected(final String text, final String message) {
    assertThatThrownBy(() -> parse(text))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining(message);
  }
}
