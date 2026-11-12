package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An implemented OpenAPI rule has the severity vacuum gives it: error, warn as warning, info as
 * note.
 */
class VacuumSeverityTest {

  private static final Map<String, AnalysisSeverity> SEVERITIES =
      Map.of(
          "error", AnalysisSeverity.ERROR,
          "warn", AnalysisSeverity.WARNING,
          "info", AnalysisSeverity.NOTE);

  private static Map<String, String> severities() {
    final Map<String, String> severities = new HashMap<>();
    try (InputStream stream =
        VacuumSeverityTest.class.getResourceAsStream("/catalog/upstream/vacuum-0.39.3.txt")) {
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.startsWith("#") && !line.isBlank())
          .map(line -> line.split("\t"))
          .forEach(parts -> severities.put(parts[0], parts[2]));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return severities;
  }

  @Test
  void everyImplementedOpenApiRuleKeepsTheSeverityOfItsVacuumRule() {
    final Map<String, String> upstream = severities();
    final Map<String, RuleDefinition> rules = new HashMap<>();
    CatalogRuleDefinitions.all().forEach(rule -> rules.put(rule.id(), rule));
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      final List<String> counterparts = row.counterparts().getOrDefault("vacuum", List.of());
      final boolean implemented =
          row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.OPENAPI);
      if (implemented && !counterparts.isEmpty()) {
        final AnalysisSeverity expected = SEVERITIES.get(upstream.get(counterparts.getFirst()));
        final AnalysisSeverity actual = rules.get(row.idFor(AnalysisLanguage.OPENAPI)).severity();
        if (expected != actual) {
          problems.add(row.name() + " is " + actual + " but vacuum says " + expected);
        }
      }
    }

    assertThat(problems).isEmpty();
  }

  @Test
  void everyPinnedVacuumRuleHasASeverity() {
    assertThat(severities().values()).isSubsetOf(SEVERITIES.keySet());
    assertThat(severities()).hasSameSizeAs(UpstreamLists.pinned().get("vacuum"));
  }
}
