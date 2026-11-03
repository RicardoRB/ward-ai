package dev.ward.lang.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ids and measurements declared in the Swift rule file. */
final class SwiftRuleFileTest {

  private static final int RULE_COUNT = 6;
  private static final Set<String> MEASUREMENTS =
      Set.of("lines", "parameters", "decision-points", "identifier-case");

  @Test
  void ruleIdsAreUniqueAndFollowTheSwiftNamespace() {
    final List<String> ids = SwiftRuleRuns.rules().stream().map(RuleDefinition::id).toList();

    assertThat(ids).doesNotHaveDuplicates();
    assertThat(ids).allMatch(id -> id.matches("ward:swift/[a-z-]+"));
  }

  @Test
  void everyRowNamesOneOfTheFourMeasurements() {
    final List<String> measurements =
        SwiftRuleRuns.rules().stream().map(rule -> measurementOf(rule.body())).toList();

    assertThat(measurements).allMatch(MEASUREMENTS::contains);
    assertThat(measurements).hasSize(RULE_COUNT);
  }

  private static String measurementOf(final String body) {
    return Arrays.stream(body.split(";"))
        .map(String::trim)
        .filter(part -> part.startsWith("measure="))
        .map(part -> part.substring("measure=".length()))
        .findFirst()
        .orElse("");
  }
}
