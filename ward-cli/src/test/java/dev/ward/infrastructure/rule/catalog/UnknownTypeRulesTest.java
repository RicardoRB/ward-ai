package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.infrastructure.rule.catalog.RuleCatalogFixturesTest.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * A rule whose decision depends on a type reports nothing when the engine cannot resolve the type
 * (#698): the violating fixture of every Java rule with capability {@code TYPES} that ships type
 * facts reports no finding when the facts are unknown.
 */
class UnknownTypeRulesTest {
  /**
   * Rules of capability {@code TYPES} whose decision does not rest on a resolved type: the null
   * flow rules follow {@code null} literals and annotations, and the others read a constructor or
   * a package name written in the source.
   */
  private static final Set<String> SYNTACTIC =
      Set.of(
          "ward:java/avoid-instantiating-objects-in-loops",
          "ward:java/assign-field-nullable",
          "ward:java/internal-api-usage",
          "ward:java/method-no-init",
          "ward:java/pass-nullable",
          "ward:java/return-nullable",
          "ward:java/string-instantiation",
          "ward:java/np-always-null",
          "ward:java/np-always-null-exception",
          "ward:java/np-argument-might-be-null",
          "ward:java/np-closing-null",
          "ward:java/np-guaranteed-deref",
          "ward:java/np-guaranteed-deref-on-exception-path",
          "ward:java/np-nonnull-return-violation",
          "ward:java/np-null-on-some-path",
          "ward:java/np-null-on-some-path-exception",
          "ward:java/np-null-on-some-path-might-be-infeasible",
          "ward:java/np-store-into-nonnull-field",
          "ward:java/rcn-redundant-nullcheck-would-have-been-a-npe",
          "ward:java/tq-comparing-values-with-incompatible-type-qualifiers"
      );
  private static final TypeFacts UNKNOWN = FixtureTypeFacts.of(Optional.empty());

  @Test
  void aJavaTypesRuleReportsNothingWhenTheTypeIsUnknown() throws IOException {
    final Map<String, RuleDefinition> rules =
        CatalogRuleDefinitions.all().stream()
            .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final List<String> reported = new ArrayList<>();
    for (final Pair pair : (Iterable<Pair>)RuleCatalogFixturesTest.pairs()::iterator) {
      final RuleDefinition rule = rules.get(pair.toString());
      final RuleFixtureSet fixtures =
          RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language());
      if (pair.language() == AnalysisLanguage.JAVA
          && rule.capability() == RuleCapability.TYPES
          && !SYNTACTIC.contains(rule.id())
          && fixtures.typeFacts().isPresent()) {
        for (final String name : fixtures.cases()) {
          final Path file = fixtures.violating(name);
          final RuleDefinition configured = RuleOptions.apply(rule, fixtures.options(name));
          final List<AnalysisFinding> findings =
              runner.run(
                  configured,
                  file.getFileName().toString(),
                  Files.readString(file),
                  UNKNOWN
              );
          if (!findings.isEmpty()) {
            reported.add(rule.id() + " " + file.getFileName());
          }
        }
      }
    }

    assertThat(reported).isEmpty();
  }
}
