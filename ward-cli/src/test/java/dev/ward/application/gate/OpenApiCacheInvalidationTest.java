package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An OpenAPI rule can depend on a referenced file, so its result is never served from a unit. */
class OpenApiCacheInvalidationTest {

  private static final String SPEC = "api.yaml";
  private static final String PART = "pets.yaml";
  private static final String SPEC_TEXT =
      "openapi: 3.0.0\npaths:\n  /pets:\n    $ref: 'pets.yaml'\n";
  private static final String RULE = "ward:openapi/operation-operation-id";

  private final Map<String, String> entries = new HashMap<>();
  private final AnalysisCache cache = AnalysisCacheFixtures.memory(entries);
  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource()
          .load().rules().stream().filter(rule -> RULE.equals(rule.id())).toList();
  private final UnitResultCache results =
      new UnitResultCache(
          new RuleEngine(new DeclarativeRuleMatcher()),
          AnalysisCacheFixtures.codec(),
          "test",
          List.of());

  private List<AnalysisFinding> run(final String partText) {
    final List<SourceUnit> units =
        List.of(
            new SourceUnit(SPEC, AnalysisLanguage.OPENAPI, SPEC_TEXT),
            new SourceUnit(PART, AnalysisLanguage.OPENAPI, partText));
    return results.run(cache, units, frontends, rules).findings();
  }

  @Test
  void editingAReferencedFileChangesTheFindingOnAWarmCache() {
    final List<AnalysisFinding> cold = run("get:\n  summary: x\n");
    final int stored = entries.size();
    final List<AnalysisFinding> warm = run("get:\n  summary: x\n");
    final List<AnalysisFinding> edited = run("get:\n  operationId: listPets\n");

    assertThat(rules).hasSize(1);
    assertThat(stored).isPositive();
    assertThat(cold)
        .singleElement()
        .satisfies(finding -> assertThat(finding.file()).isEqualTo(PART));
    assertThat(warm).isEqualTo(cold);
    assertThat(edited).isEmpty();
  }

  @Test
  void editingTheEntryFileChangesTheFindingToo() {
    run("get:\n  summary: x\n");
    final List<SourceUnit> units =
        List.of(
            new SourceUnit(SPEC, AnalysisLanguage.OPENAPI, "openapi: 3.0.0\npaths: {}\n"),
            new SourceUnit(PART, AnalysisLanguage.OPENAPI, "get:\n  summary: x\n"));

    assertThat(results.run(cache, units, frontends, rules).findings()).isEmpty();
  }
}
