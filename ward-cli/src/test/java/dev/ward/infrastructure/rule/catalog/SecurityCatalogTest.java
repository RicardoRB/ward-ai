package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks the provenance conditions of the security catalog on the real catalog and on fixtures. */
class SecurityCatalogTest {

  private static final Set<String> CWE = Set.of("CWE-79", "CWE-89");

  private static CatalogRow row(
      final String name, final String source, final List<String> cwe, final List<String> sources) {
    return new CatalogRow(
        name,
        "",
        Set.of(),
        Map.of(AnalysisLanguage.JAVA, LanguageStatus.implemented()),
        Map.of(),
        Map.of(),
        source,
        Optional.empty(),
        cwe,
        sources);
  }

  @Test
  void theCatalogSatisfiesEveryCondition() {
    assertThat(
            SecurityCatalogRules.problems(
                new RuleCatalogReader().load().rows(),
                SecurityCatalogRules.pinnedCweIds(),
                CatalogRuleDefinitions.all()))
        .isEmpty();
  }

  @Test
  void aRowWithoutCweOrSourcesIsRejected() {
    final CatalogRow bare = row("xss", SecurityCatalogRules.FILE, List.of(), List.of());

    assertThat(SecurityCatalogRules.problems(List.of(bare), CWE, List.of()))
        .containsExactly("row xss has no cwe", "row xss has no sources");
  }

  @Test
  void aCweIdOutsideThePinnedListIsRejected() {
    final CatalogRow odd =
        row("xss", SecurityCatalogRules.FILE, List.of("CWE-79", "CWE-99999"), List.of("https://x"));

    assertThat(SecurityCatalogRules.problems(List.of(odd), CWE, List.of()))
        .containsExactly("row xss names CWE-99999, which is not in the pinned list");
  }

  @Test
  void aSourceThatIsNotAnHttpsUrlIsRejected() {
    final CatalogRow local =
        row("xss", SecurityCatalogRules.FILE, List.of("CWE-79"), List.of("notes.txt"));

    assertThat(SecurityCatalogRules.problems(List.of(local), CWE, List.of()))
        .containsExactly("row xss has a source that is not an https URL: notes.txt");
  }

  @Test
  void aRowOfAnotherFileNeedsNoCwe() {
    final CatalogRow other = row("xss", "catalog/pmd.toml", List.of(), List.of());

    assertThat(SecurityCatalogRules.problems(List.of(other), CWE, List.of())).isEmpty();
  }

  @Test
  void aSecurityRuleWithoutARowIsRejected() {
    final RuleDefinition orphan =
        CatalogRuleDefinitions.all().stream()
            .filter(rule -> "security".equals(rule.category()))
            .findFirst()
            .orElseThrow();

    assertThat(SecurityCatalogRules.problems(List.of(), CWE, List.of(orphan)))
        .containsExactly("rule " + orphan.id() + " has no catalog row");
  }
}
