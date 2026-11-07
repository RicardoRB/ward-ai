package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.TypeFactsRun;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.jvm.NativeJvmTypes;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The first type-aware rule, run through the engine on the facts of the native JVM resolver. */
class EqualsOnUnrelatedTypesNativeTest {
  private static final String RULE_ID = "ward:java/equals-on-unrelated-types";
  private static final String SOURCE =
      """
      class A {
        boolean unrelated(String name, Integer count) {
          return name.equals(count);
        }

        boolean same(String name, String other) {
          return name.equals(other);
        }

        boolean open(String name, Object any, Missing gone) {
          return name.equals(any) || name.equals(gone);
        }
      }
      """;

  @TempDir Path project;

  @Test
  void reportsOnlyTheCallBetweenTwoDifferentFinalTypes() {
    final NativeJvmTypes types = new NativeJvmTypes(project);
    try {
      final RuleDefinition rule =
          CatalogRuleDefinitions.all().stream()
              .filter(definition -> definition.id().equals(RULE_ID))
              .findFirst()
              .orElseThrow();
      final TypeFactsRun run =
          new TypeFactsRun(
              Map.of(AnalysisLanguage.JAVA, types.facts(Optional.empty()).orElseThrow()));

      final RuleRunResult result =
          new RuleEngine(BundledRuleMatchers.create())
              .run(
                  List.of(new SourceUnit("A.java", AnalysisLanguage.JAVA, SOURCE)),
                  run.withTypes(GateFrontends.create(new AntlrLanguageFrontend())),
                  List.of(rule));

      assertThat(result.notRun()).isEmpty();
      assertThat(result.executionErrors()).isEmpty();
      assertThat(result.findings())
          .singleElement()
          .satisfies(finding -> assertThat(finding.line()).isEqualTo(3));
    } finally {
      types.close();
    }
  }

  @Test
  void withoutAResolverTheRuleIsReportedAsNotRun() {
    final RuleDefinition rule =
        CatalogRuleDefinitions.all().stream()
            .filter(definition -> definition.id().equals(RULE_ID))
            .findFirst()
            .orElseThrow();

    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(new SourceUnit("A.java", AnalysisLanguage.JAVA, SOURCE)),
                GateFrontends.create(new AntlrLanguageFrontend()),
                List.of(rule));

    assertThat(result.notRun()).extracting(notRun -> notRun.ruleId()).containsExactly(RULE_ID);
    assertThat(result.findings()).isEmpty();
  }
}
