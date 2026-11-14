package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** What the Kotlin frontend declares, and which rules that switches on. */
class GateFrontendsKotlinTest {

  private static final Set<RuleCapability> GRAPH_CAPABILITIES =
      Set.of(
          RuleCapability.CFG,
          RuleCapability.DATAFLOW,
          RuleCapability.TAINT,
          RuleCapability.SYMBOLS);

  private static final int TAINT_RULES = 18;

  private final FrontendSupport kotlin =
      GateFrontends.create(new AntlrLanguageFrontend()).stream()
          .filter(support -> support.language() == AnalysisLanguage.KOTLIN)
          .findFirst()
          .orElseThrow();

  @Test
  void kotlinProvidesGraphsDataFlowAndTaintButNotSymbols() {
    assertThat(kotlin.capabilities())
        .containsExactlyInAnyOrder(
            RuleCapability.SYNTAX,
            RuleCapability.SCOPES,
            RuleCapability.CFG,
            RuleCapability.DATAFLOW,
            RuleCapability.TAINT);
  }

  @Test
  void theLoweringOfTheFrontendGivesAKotlinFunctionAGraph() {
    final SourceUnit unit = new SourceUnit("A.kt", AnalysisLanguage.KOTLIN, "fun f() { g() }\n");

    assertThat(kotlin.lowering().lower(unit, kotlin.frontend().parse(unit).tree()).graphs())
        .hasSize(1);
  }

  @Test
  void theKotlinRulesThatNeedAGraphAreExactlyTheTaintRulesAndNoOtherRuleChangesItsLanguages() {
    final Set<String> graphRules =
        new ClasspathRuleSource()
            .load().rules().stream()
                .filter(rule -> rule.languages().contains(AnalysisLanguage.KOTLIN))
                .filter(rule -> GRAPH_CAPABILITIES.contains(rule.capability()))
                .map(RuleDefinition::id)
                .collect(Collectors.toSet());

    assertThat(graphRules).allMatch(id -> id.startsWith("ward:kotlin/"));
    assertThat(graphRules).hasSize(TAINT_RULES);
  }

  @Test
  void theLayerRuleStillNamesNoKotlinSoItStillChecksNoKotlinFile() {
    assertThat(ArchitectureLayers.ENGINE_LANGUAGES).doesNotContain(AnalysisLanguage.KOTLIN);
    assertThat(kotlin.capabilities()).doesNotContain(RuleCapability.SYMBOLS);
  }
}
